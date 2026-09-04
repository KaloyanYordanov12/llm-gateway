package dev.kaloyanyordanov.llmgateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

    private final RateLimiterService rateLimiter = mock(RateLimiterService.class);
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final RateLimitFilter filter = new RateLimitFilter(rateLimiter, jsonMapper);

    private static Client clientWithId(long id) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(id);
        return client;
    }

    @Test
    void withinLimitProceeds() throws Exception {
        when(rateLimiter.tryAcquire(anyLong(), any())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE, clientWithId(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).as("chain proceeded").isNotNull();
    }

    @Test
    void overLimitReturns429Envelope() throws Exception {
        when(rateLimiter.tryAcquire(anyLong(), any())).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE, clientWithId(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"error\"")
                .contains("rate_limit_error")
                .contains("request_id");
        assertThat(chain.getRequest()).as("chain did not proceed").isNull();
    }

    @Test
    void passesTheClientsOwnLimitToTheRateLimiter() throws Exception {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(9L);
        when(client.getRateLimit()).thenReturn(5);
        when(rateLimiter.tryAcquire(9L, 5)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE, client);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).as("chain proceeded").isNotNull();
    }

    @Test
    void withoutAuthenticatedClientProceedsWithoutRateLimiting() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(rateLimiter);
    }
}
