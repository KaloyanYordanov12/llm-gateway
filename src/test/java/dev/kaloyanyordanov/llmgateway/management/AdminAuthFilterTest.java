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
    private final AdminAuthFilter filter = new AdminAuthFilter("admin-secret", jsonMapper);

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
}
