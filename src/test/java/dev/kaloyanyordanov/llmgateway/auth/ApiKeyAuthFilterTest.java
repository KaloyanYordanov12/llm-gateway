package dev.kaloyanyordanov.llmgateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class ApiKeyAuthFilterTest {

    private final ClientAuthenticator authenticator = mock(ClientAuthenticator.class);
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(authenticator, jsonMapper);

    @Test
    void validKeyProceedsAndExposesClient() throws Exception {
        Client client = new Client("acme", "hash", true);
        when(authenticator.authenticate("secret")).thenReturn(Optional.of(client));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE)).isSameAs(client);
        assertThat(chain.getRequest()).as("chain proceeded").isNotNull();
    }

    @Test
    void missingKeyIsRejectedWithErrorEnvelope() throws Exception {
        when(authenticator.authenticate(null)).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("x-api-key");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"error\"")
                .contains("authentication_error")
                .contains("request_id");
        assertThat(chain.getRequest()).as("chain did not proceed").isNull();
    }

    @Test
    void invalidKeyIsRejected() throws Exception {
        when(authenticator.authenticate("bad")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(chain.getRequest()).isNull();
    }
}
