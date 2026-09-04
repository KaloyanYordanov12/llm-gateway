package dev.kaloyanyordanov.llmgateway.metrics;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kaloyanyordanov.llmgateway.AbstractPostgresIntegrationTest;
import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.management.AdminAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Prometheus scrape is served only through the admin-guarded {@code
 * /api/metrics} route and is not on the public actuator surface — a public metrics
 * dump would leak internals.
 */
@AutoConfigureMockMvc
class MetricsEndpointIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ADMIN_KEY = "dev-admin-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void scrapeRequiresTheAdminKey() throws Exception {
        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aClientKeyCannotReachTheScrape() throws Exception {
        mockMvc.perform(get("/api/metrics").header(ApiKeyAuthFilter.API_KEY_HEADER, "some-client-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminKeyGetsThePrometheusScrape() throws Exception {
        mockMvc.perform(get("/api/metrics").header(AdminAuthFilter.ADMIN_KEY_HEADER, ADMIN_KEY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# TYPE")));
    }

    @Test
    void prometheusIsNotOnThePublicActuatorSurface() throws Exception {
        // Only /actuator/health is exposed; the prometheus actuator endpoint is not.
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isNotFound());
    }
}
