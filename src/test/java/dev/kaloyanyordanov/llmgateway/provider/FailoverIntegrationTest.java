package dev.kaloyanyordanov.llmgateway.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "gateway.provider.chain=anthropic,openai")
class FailoverIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_KEY = "secret-key";

    // Each provider-reaching request uses a distinct prompt so the shared response
    // cache never turns a later request into a hit (failover only matters on misses).
    private static String body(String marker) {
        return "{\"model\":\"claude-3-5-sonnet-20241022\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + marker + "\"}],\"max_tokens\":100}";
    }

    private static final String PRIMARY_BODY = """
            {"id":"msg_p","type":"message","role":"assistant","model":"claude-3-5-sonnet-20241022",
             "content":"from primary","stop_reason":"end_turn",
             "usage":{"input_tokens":5,"output_tokens":3}}""";
    private static final String SECONDARY_BODY = """
            {"id":"chatcmpl_s","model":"gpt-4o",
             "choices":[{"index":0,"message":{"role":"assistant","content":"from secondary"},
             "finish_reason":"stop"}],
             "usage":{"prompt_tokens":4,"completion_tokens":2}}""";

    private static final WireMockServer PRIMARY = new WireMockServer(options().dynamicPort());
    private static final WireMockServer SECONDARY = new WireMockServer(options().dynamicPort());

    static {
        PRIMARY.start();
        SECONDARY.start();
    }

    @DynamicPropertySource
    static void providerUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.base-url", PRIMARY::baseUrl);
        registry.add("gateway.provider.openai.base-url", SECONDARY::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        PRIMARY.resetAll();
        SECONDARY.resetAll();
        circuitBreakerRegistry.circuitBreaker("anthropic").reset();
        circuitBreakerRegistry.circuitBreaker("openai").reset();
        clientRepository.save(new Client("acme", passwordEncoder.encode(VALID_KEY), true));
    }

    private void sendExpectingContent(String marker, int expectedStatus, String expectedContent)
            throws Exception {
        var result = mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body(marker)))
                .andExpect(status().is(expectedStatus));
        if (expectedContent != null) {
            result.andExpect(jsonPath("$.content").value(expectedContent));
        }
    }

    @Test
    void failsOverToSecondaryWhenPrimaryReturnsServerError() throws Exception {
        PRIMARY.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(serverError()));
        SECONDARY.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(SECONDARY_BODY)));

        // The client never sees the switch — it just gets a 200 from the secondary.
        sendExpectingContent("failover-case", 200, "from secondary");
        SECONDARY.verify(WireMock.postRequestedFor(urlEqualTo("/v1/chat/completions")));
    }

    @Test
    void returnsServiceUnavailableWhenBothProvidersAreDown() throws Exception {
        PRIMARY.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(serverError()));
        SECONDARY.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions")).willReturn(serverError()));

        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body("both-down-case")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.type").value("overloaded_error"));
    }

    @Test
    void trafficReturnsToPrimaryAfterItRecovers() throws Exception {
        // Primary circuit forced open -> traffic lands on the secondary.
        CircuitBreaker primaryBreaker = circuitBreakerRegistry.circuitBreaker("anthropic");
        primaryBreaker.transitionToOpenState();
        SECONDARY.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(SECONDARY_BODY)));
        sendExpectingContent("recover-while-open", 200, "from secondary");

        // Primary recovers: half-open probe succeeds, traffic returns to primary.
        primaryBreaker.transitionToHalfOpenState();
        PRIMARY.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(okJson(PRIMARY_BODY)));
        sendExpectingContent("recover-after-primary-up", 200, "from primary");
    }
}
