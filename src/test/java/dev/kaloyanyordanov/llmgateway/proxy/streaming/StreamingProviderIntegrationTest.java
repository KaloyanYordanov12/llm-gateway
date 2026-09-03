package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpServerErrorException;

class StreamingProviderIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String SSE_BODY = String.join("\n",
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\",\"model\":\"claude-x\","
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}",
            "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}",
            "data: {\"type\":\"message_stop\"}");

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void providerUrl(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.base-url", WIREMOCK::baseUrl);
    }

    @Autowired
    private StreamingProviderClient streamingProviderClient;

    @BeforeEach
    void reset() {
        WIREMOCK.resetAll();
    }

    private static MessagesRequest streamingRequest() {
        return new MessagesRequest(
                "claude-x", null, List.of(new Message("user", "hi")), null, null, 100, null, true);
    }

    @Test
    void readsUpstreamSseIntoAnAssembledResult() {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/event-stream")
                .withBody(SSE_BODY)));

        List<String> deltas = new ArrayList<>();
        StreamResult result = streamingProviderClient.stream(streamingRequest(), deltas::add);

        assertThat(result.content()).isEqualTo("Hello world");
        assertThat(result.inputTokens()).isEqualTo(10);
        assertThat(result.outputTokens()).isEqualTo(5);
        assertThat(result.complete()).isTrue();
        assertThat(deltas).containsExactly("Hello", " world");
    }

    @Test
    void serverErrorBeforeStreamBodyThrowsFailoverTrigger() {
        WIREMOCK.stubFor(post(urlEqualTo("/v1/messages")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> streamingProviderClient.stream(streamingRequest(), delta -> { }))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
