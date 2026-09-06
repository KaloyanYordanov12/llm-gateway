package dev.kaloyanyordanov.llmgateway.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The other half of the demo-view matrix: with the shipped default
 * ({@code gateway.provider.mode=live}, unset here) there is no read exemption. The
 * same read paths that a demo box serves publicly are locked without the admin
 * key, so a real deployment leaks nothing. This is the fail-secure default; the
 * demo relaxation is proven separately in {@link DemoModePublicReadIntegrationTest}.
 */
@AutoConfigureMockMvc
class LiveModeLockedIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ADMIN_KEY = "dev-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedGetStatsIsRejected() throws Exception {
        mockMvc.perform(get("/api/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedGetClientsIsRejected() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedGetUsageIsRejected() throws Exception {
        mockMvc.perform(get("/api/usage").param("client", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminKeyStillReadsStats() throws Exception {
        // Behavior unchanged from today: the admin key opens the read paths.
        mockMvc.perform(get("/api/stats").header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk());
    }
}
