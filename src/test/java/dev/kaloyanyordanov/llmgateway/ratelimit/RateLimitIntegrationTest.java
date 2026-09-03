package dev.kaloyanyordanov.llmgateway.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "gateway.rate-limit.capacity=2",
        "gateway.rate-limit.refill-period=1h"
})
class RateLimitIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String VALID_KEY = "secret-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedClient() {
        clientRepository.deleteAll();
        clientRepository.save(new Client("acme", passwordEncoder.encode(VALID_KEY), true));
    }

    private void authenticatedRequest(int expectedStatus) throws Exception {
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void allowsUpToCapacityThenRejectsWith429() throws Exception {
        // Capacity is 2: first two authenticated requests pass the limiter (they
        // then 404 as no proxy handler exists yet); the third is rate-limited.
        authenticatedRequest(404);
        authenticatedRequest(404);

        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.error.type").value("rate_limit_error"))
                .andExpect(jsonPath("$.request_id").exists());
    }
}
