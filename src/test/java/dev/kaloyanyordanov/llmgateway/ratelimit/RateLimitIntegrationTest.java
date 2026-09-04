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

    private static final String LIMITED_KEY = "limited-key";

    @BeforeEach
    void seedClient() {
        clientRepository.deleteAll();
        clientRepository.save(new Client("acme", passwordEncoder.encode(VALID_KEY), true));
        // A client whose own limit (1) is stricter than the global default (2).
        clientRepository.save(
                new Client("limited", passwordEncoder.encode(LIMITED_KEY), true, 1, null));
    }

    private static final String BODY =
            "{\"model\":\"claude-3-5-sonnet-20241022\","
            + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":10}";

    private void authenticatedRequest(int expectedStatus) throws Exception {
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void allowsUpToCapacityThenRejectsWith429() throws Exception {
        // Capacity is 2: the first two authenticated requests pass the limiter and
        // reach the proxy handler (which 5xx's on the unreachable provider); the
        // third is rejected by the rate limiter with 429 before any provider call.
        authenticatedRequest(502);
        authenticatedRequest(502);

        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.error.type").value("rate_limit_error"))
                .andExpect(jsonPath("$.request_id").exists());
    }

    @Test
    void perClientLimitIsEnforcedIndependentlyOfTheGlobalDefault() throws Exception {
        // The limited client's own cap is 1, below the global default of 2: its
        // first request reaches the proxy (502 on the unreachable provider), its
        // second is rejected with 429 — one earlier than a default client would be.
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, LIMITED_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(502));

        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, LIMITED_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.type").value("rate_limit_error"));
    }
}
