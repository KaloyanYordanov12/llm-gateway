package dev.kaloyanyordanov.llmgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MetricsFilterTest {

    private final GatewayMetrics metrics = mock(GatewayMetrics.class);
    private final LatencyTracker latencyTracker = mock(LatencyTracker.class);
    private final MetricsFilter filter = new MetricsFilter(metrics, latencyTracker);

    @Test
    void recordsLatencyAndRequestForTheAuthenticatedClient() throws Exception {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(7L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE, client);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("chain proceeded").isNotNull();
        verify(latencyTracker).record(anyLong());
        verify(metrics).recordRequest(eq(7L), anyLong());
    }

    @Test
    void recordsStillEvenWithoutAnAuthenticatedClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(latencyTracker).record(anyLong());
        verify(metrics).recordRequest(eq(-1L), anyLong());
    }
}
