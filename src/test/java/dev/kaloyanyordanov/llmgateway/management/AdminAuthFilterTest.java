package dev.kaloyanyordanov.llmgateway.management;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class AdminAuthFilterTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    // Live mode (no demo read exemption) unless a test opts into demo below.
    private final AdminAuthFilter filter = new AdminAuthFilter("admin-secret", false, jsonMapper);
    private final AdminAuthFilter demoFilter = new AdminAuthFilter("admin-secret", true, jsonMapper);

    @Test
    void correctKeyProceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminAuthFilter.ADMIN_KEY_HEADER, "admin-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingKeyIsRejectedWithEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString())
                .contains("authentication_error")
                .contains("request_id");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AdminAuthFilter.ADMIN_KEY_HEADER, "not-the-admin-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void demoModeAllowsUnauthenticatedGetOnReadPaths() throws Exception {
        for (String path : new String[] {"/api/stats", "/api/clients", "/api/usage"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            demoFilter.doFilter(request, response, chain);

            assertThat(response.getStatus()).as(path).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest()).as(path).isNotNull();
        }
    }

    @Test
    void demoModeStillRejectsUnauthenticatedWrites() throws Exception {
        for (String method : new String[] {"POST", "PATCH"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/clients");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            demoFilter.doFilter(request, response, chain);

            assertThat(response.getStatus()).as(method).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(chain.getRequest()).as(method).isNull();
        }
    }

    @Test
    void demoModeDoesNotExemptNonReadPaths() throws Exception {
        // Only the exact read paths are opened; /api/metrics and the like stay gated.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        demoFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void liveModeDoesNotExemptReadPaths() throws Exception {
        // Same GET the demo filter would pass is rejected without a key in live mode.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stats");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void demoModeMatchesReadPathBeneathAServletContextPath() throws Exception {
        // The exemption matches the path within the application, so a deployment
        // under a context path (e.g. /gw) still opens the read paths in demo mode.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gw/api/stats");
        request.setContextPath("/gw");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        demoFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void demoModeStillAcceptsTheAdminKey() throws Exception {
        // The exemption is additive: a valid admin key still passes, including for writes.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");
        request.addHeader(AdminAuthFilter.ADMIN_KEY_HEADER, "admin-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        demoFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isNotNull();
    }
}
