package dev.kaloyanyordanov.llmgateway.provider.routing;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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
import dev.kaloyanyordanov.llmgateway.usage.UsageRepository;
import org.assertj.core.api.Assertions;
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
@TestPropertySource(properties = {
        // Logical model "flex" is priced (so it is billable) and routed to OpenAI's gpt-4o-mini.
        "gateway.pricing.models.flex.input-per-1k=0.001",
        "gateway.pricing.models.flex.output-per-1k=0.002",
        "gateway.routing.rules[0].model=flex",
        "gateway.routing.rules[0].targets[0].provider=openai",
        "gateway.routing.rules[0].targets[0].model=gpt-4o-mini"
})
class RoutingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_KEY = "secret-key";

    private static final WireMockServer OPENAI = new WireMockServer(options().dynamicPort());

    static {
        OPENAI.start();
    }

    @DynamicPropertySource
    static void openAiUrl(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.openai.base-url", OPENAI::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsageRepository usageRepository;

    @BeforeEach
    void setUp() {
        OPENAI.resetAll();
        clientRepository.save(new Client("acme", passwordEncoder.encode(VALID_KEY), true));
    }

    @Test
    void logicalModelRoutesToConcreteUpstreamTargetAndBillsLogical() throws Exception {
        OPENAI.stubFor(WireMock.post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"id":"chatcmpl-1","model":"gpt-4o-mini",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"routed to openai"},
                 "finish_reason":"stop"}],
                 "usage":{"prompt_tokens":7,"completion_tokens":3}}""")));

        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"flex\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                                + "\"max_tokens\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("routed to openai"));

        // The concrete model was sent upstream, not the logical one.
        OPENAI.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing("gpt-4o-mini")));

        // Usage is billed against the logical model the client requested.
        Assertions.assertThat(usageRepository.findAll())
                .singleElement()
                .satisfies(record -> Assertions.assertThat(record.getModel()).isEqualTo("flex"));
    }

    @Test
    void unpricedModelStillReturns400() throws Exception {
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"totally-unknown\","
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"));
    }
}
