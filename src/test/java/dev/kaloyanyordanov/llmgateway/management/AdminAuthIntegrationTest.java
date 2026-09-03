package dev.kaloyanyordanov.llmgateway.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminAuthIntegrationTest extends AbstractPostgresIntegrationTest {

    // Matches the ManagementProperties default admin key used when unset.
    private static final String ADMIN_KEY = "dev-admin-key";
    private static final String ANY_MANAGEMENT_PATH = "/api/ping";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingAdminKeyIsRejected() throws Exception {
        mockMvc.perform(get(ANY_MANAGEMENT_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.type").value("authentication_error"));
    }

    @Test
    void wrongAdminKeyIsRejected() throws Exception {
        mockMvc.perform(get(ANY_MANAGEMENT_PATH).header(AdminAuthFilter.ADMIN_KEY_HEADER, "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aClientApiKeyCannotReachManagement() throws Exception {
        mockMvc.perform(get(ANY_MANAGEMENT_PATH).header(ApiKeyAuthFilter.API_KEY_HEADER, "some-client-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctAdminKeyPassesTheFilter() throws Exception {
        // No such endpoint, so passing the admin filter falls through to 404 —
        // the point is it is NOT 401.
        mockMvc.perform(get(ANY_MANAGEMENT_PATH).header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isNotFound());
    }
}
