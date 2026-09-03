package dev.kaloyanyordanov.llmgateway;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the built React SPA is packaged into the app's static resources and
 * served by Spring at the root (one self-contained deployable).
 */
@AutoConfigureMockMvc
class DashboardIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardIndexIsServedAtRoot() throws Exception {
        // "/" resolves to the welcome page (a forward MockMvc doesn't follow), so
        // request the static resource directly to assert the built SPA is served.
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"root\">")));
    }
}
