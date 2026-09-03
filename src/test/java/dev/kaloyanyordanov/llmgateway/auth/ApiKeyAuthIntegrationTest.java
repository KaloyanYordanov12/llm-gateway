package dev.kaloyanyordanov.llmgateway.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApiKeyAuthIntegrationTest extends AbstractPostgresIntegrationTest {

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

    @Test
    void missingKeyIsRejectedWithEnvelope() throws Exception {
        mockMvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.error.type").value("authentication_error"))
                .andExpect(jsonPath("$.request_id").exists());
    }

    @Test
    void invalidKeyIsRejected() throws Exception {
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, "not-the-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("authentication_error"));
    }

    @Test
    void validKeyPassesAuthentication() throws Exception {
        // No proxy handler exists yet (Phase 3), so a valid key passes auth and
        // then falls through to 404 — the point is it is NOT 401.
        mockMvc.perform(post("/v1/messages")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorHealthIsNotBehindApiKey() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
