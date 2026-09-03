package dev.kaloyanyordanov.llmgateway.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Drives the real Anthropic adapter (via the resilient wrapper) against WireMock,
 * exercising retry recovery and the full circuit-breaker state machine with
 * simulated 5xx failures.
 */
class ProviderResilienceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.base-url", WIREMOCK::baseUrl);
    }

    @Autowired
    private ProviderRegistry providerRegistry;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("anthropic");
        circuitBreaker.reset();
    }

    private static MessagesRequest request() {
        return new MessagesRequest("m", null, List.of(new Message("user", "hi")), null, null, 10, null);
    }

    private static final String OK_BODY = """
            {"id":"msg_1","type":"message","role":"assistant","model":"m",
             "content":"hi","stop_reason":"end_turn",
             "usage":{"input_tokens":1,"output_tokens":1}}""";

    @Test
    void retryRecoversFromTransientServerError() {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages")).inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("recovered"));
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages")).inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson(OK_BODY)));

        assertThat(providerRegistry.getDefault().createMessage(request()).id()).isEqualTo("msg_1");
        WIREMOCK.verify(2, postRequestedFor(urlEqualTo("/v1/messages")));
    }

    @Test
    void circuitOpensOnSustainedFailuresThenClosesOnRecovery() {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages")).willReturn(serverError()));

        // Enough failing calls to fill the window and trip the breaker.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> providerRegistry.getDefault().createMessage(request()));
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Open circuit fails fast with no further provider calls.
        WIREMOCK.resetRequests();
        assertThatThrownBy(() -> providerRegistry.getDefault().createMessage(request()))
                .isInstanceOf(CallNotPermittedException.class);
        WIREMOCK.verify(0, postRequestedFor(urlEqualTo("/v1/messages")));

        // Recover: provider healthy, half-open trial calls close the breaker.
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages")).willReturn(okJson(OK_BODY)));
        circuitBreaker.transitionToHalfOpenState();
        for (int i = 0; i < 3; i++) {
            providerRegistry.getDefault().createMessage(request());
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
