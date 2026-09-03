package dev.kaloyanyordanov.llmgateway.proxy;

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
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class CacheIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_KEY = "secret-key";
    private static final String BODY =
            "{\"model\":\"claude-3-5-sonnet-20241022\","
            + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":100}";

    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.provider.base-url", WIREMOCK::baseUrl);
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
        WIREMOCK.resetAll();
        clientRepository.save(new Client("acme", passwordEncoder.encode(VALID_KEY), true));
    }

    private void sendIdenticalRequest() throws Exception {
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("msg_cache"));
    }

    @Test
    void identicalRequestsHitTheProviderOnlyOnce() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlEqualTo("/v1/messages")).willReturn(okJson("""
                {"id":"msg_cache","type":"message","role":"assistant","model":"claude-x",
                 "content":"cached hello","stop_reason":"end_turn",
                 "usage":{"input_tokens":3,"output_tokens":2}}""")));

        sendIdenticalRequest();
        sendIdenticalRequest();

        // Second request is served from cache: the provider is called only once,
        // and usage is recorded exactly once (no double-counting on cache hits).
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/v1/messages")));
        Assertions.assertThat(usageRepository.findAll()).hasSize(1);
    }
}
