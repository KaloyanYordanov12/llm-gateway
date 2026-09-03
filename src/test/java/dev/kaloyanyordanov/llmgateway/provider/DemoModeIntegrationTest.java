package dev.kaloyanyordanov.llmgateway.provider;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "gateway.provider.mode=demo")
@AutoConfigureMockMvc
class DemoModeIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_KEY = "secret-key";
    private static final String BODY =
            "{\"model\":\"claude-3-5-sonnet-20241022\","
            + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":100}";

    @Autowired
    private ProviderRegistry providerRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsageRepository usageRepository;

    @BeforeEach
    void seed() {
        clientRepository.save(new Client("acme", passwordEncoder.encode(VALID_KEY), true));
    }

    @Test
    void demoModeSelectsTheStubProvider() {
        Assertions.assertThat(providerRegistry.getDefault().name()).isEqualTo("stub");
    }

    @Test
    void demoRequestFlowsThroughCacheAndUsageWithoutNetwork() throws Exception {
        // First request: cache miss -> stub -> usage recorded -> cached.
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("demo stub")))
                .andExpect(jsonPath("$.usage.input_tokens").value(8))
                .andExpect(jsonPath("$.usage.output_tokens").value(24));

        Assertions.assertThat(usageRepository.findAll()).hasSize(1);

        // Second identical request: cache hit, so no new usage is recorded.
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        Assertions.assertThat(usageRepository.findAll()).hasSize(1);
    }
}
