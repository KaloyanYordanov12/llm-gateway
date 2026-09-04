package dev.kaloyanyordanov.llmgateway.metrics;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Times each proxied request and records its latency and a per-client request
 * count. Runs after authentication and rate limiting, so only authenticated,
 * admitted requests are measured; a request rejected earlier (401/429) is never
 * counted as proxied traffic. For a streaming request this measures the
 * synchronous handling that sets up the SSE stream, not the full stream duration.
 */
public class MetricsFilter extends OncePerRequestFilter {

    private final GatewayMetrics metrics;
    private final LatencyTracker latencyTracker;

    public MetricsFilter(GatewayMetrics metrics, LatencyTracker latencyTracker) {
        this.metrics = metrics;
        this.latencyTracker = latencyTracker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000L;
            latencyTracker.record(millis);
            metrics.recordRequest(clientId(request), millis);
        }
    }

    private static long clientId(HttpServletRequest request) {
        Object attribute = request.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE);
        return attribute instanceof Client client ? client.getId() : -1L;
    }
}
