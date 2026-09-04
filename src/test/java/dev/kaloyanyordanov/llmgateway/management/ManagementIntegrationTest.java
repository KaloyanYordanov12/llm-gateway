package dev.kaloyanyordanov.llmgateway.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ManagementIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ADMIN_KEY = "dev-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UsageService usageService;

    private Long clientId;

    @BeforeEach
    void seed() {
        clientId = clientRepository.save(new Client("acme", "bcrypt-hash", true)).getId();
        usageService.record(clientId, "m", 100, 40, new BigDecimal("0.10"));
    }

    @Test
    void clientsEndpointReturnsClientsWithoutKeyHash() throws Exception {
        mockMvc.perform(get("/api/clients").header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("acme"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[0].api_key_hash").doesNotExist())
                .andExpect(jsonPath("$[0].apiKeyHash").doesNotExist());
    }

    @Test
    void usageEndpointReturnsPerClientTotals() throws Exception {
        mockMvc.perform(get("/api/usage").param("client", String.valueOf(clientId))
                        .header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(clientId))
                .andExpect(jsonPath("$.total_input_tokens").value(100))
                .andExpect(jsonPath("$.total_output_tokens").value(40))
                .andExpect(jsonPath("$.request_count").value(1));
    }

    @Test
    void statsEndpointReturnsAggregate() throws Exception {
        mockMvc.perform(get("/api/stats").header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_requests").value(1))
                .andExpect(jsonPath("$.total_input_tokens").value(100))
                .andExpect(jsonPath("$.rate_limit_rejections").exists())
                .andExpect(jsonPath("$.p50_millis").exists())
                .andExpect(jsonPath("$.p95_millis").exists())
                .andExpect(jsonPath("$.p99_millis").exists());
    }
}
