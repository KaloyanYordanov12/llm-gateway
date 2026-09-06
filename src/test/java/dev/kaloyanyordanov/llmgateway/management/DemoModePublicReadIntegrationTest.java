package dev.kaloyanyordanov.llmgateway.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.auth.ClientRepository;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The public read-only demo view. In {@code gateway.provider.mode=demo}, the
 * read-only telemetry paths are readable over unauthenticated {@code GET}, while
 * every write and every non-read path stays behind the admin key. This is the
 * security crux of the demo brief, proven over real HTTP.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "gateway.provider.mode=demo")
class DemoModePublicReadIntegrationTest extends AbstractPostgresIntegrationTest {

    // Matches the ManagementProperties default admin key used when unset.
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

    // ── Reads: public over unauthenticated GET ──────────────────────────────

    @Test
    void unauthenticatedGetStatsIsPublic() throws Exception {
        mockMvc.perform(get("/api/stats")).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedGetClientsIsPublic() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedGetUsageIsPublic() throws Exception {
        mockMvc.perform(get("/api/usage").param("client", String.valueOf(clientId)))
                .andExpect(status().isOk());
    }

    // ── Writes: still key-gated, even in demo mode ──────────────────────────

    @Test
    void unauthenticatedPostClientsIsRejected() throws Exception {
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedPatchClientIsRejected() throws Exception {
        mockMvc.perform(patch("/api/clients/{id}", clientId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    // ── The exemption is GET-and-read-path only, and header-blind ───────────

    @Test
    void nonReadGetIsNotExempt() throws Exception {
        // /api/metrics is a real /api/* endpoint that is NOT a public read path.
        mockMvc.perform(get("/api/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void clientApiKeyDoesNotUnlockWrites() throws Exception {
        mockMvc.perform(post("/api/clients").header(ApiKeyAuthFilter.API_KEY_HEADER, "some-client-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientApiKeyDoesNotReachNonReadPaths() throws Exception {
        mockMvc.perform(get("/api/metrics").header(ApiKeyAuthFilter.API_KEY_HEADER, "some-client-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsArePublicRegardlessOfAClientKey() throws Exception {
        // The read exemption is by (mode, method, path); a client key is simply
        // irrelevant on a public read path, not a gate that could reject it.
        mockMvc.perform(get("/api/stats").header(ApiKeyAuthFilter.API_KEY_HEADER, "some-client-key"))
                .andExpect(status().isOk());
    }

    // ── The admin key still works, for both reads and writes ────────────────

    @Test
    void adminKeyStillReadsStats() throws Exception {
        mockMvc.perform(get("/api/stats").header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void adminKeyCanStillWrite() throws Exception {
        mockMvc.perform(post("/api/clients").header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"beta\"}"))
                .andExpect(status().isCreated());
    }
}
