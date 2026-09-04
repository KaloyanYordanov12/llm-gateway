package dev.kaloyanyordanov.llmgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class GatewayMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ResponseCacheService cache = mock(ResponseCacheService.class);
    private final RateLimiterService rateLimiter = mock(RateLimiterService.class);

    private GatewayMetrics metrics() {
        return new GatewayMetrics(registry, cache, rateLimiter);
    }

    @Test
    void cacheAndRateLimitCountersAreBoundToTheLiveServiceCounters() {
        when(cache.hitCount()).thenReturn(3L);
        when(cache.missCount()).thenReturn(2L);
        when(rateLimiter.rejectionCount()).thenReturn(1L);
        metrics();

        assertThat(registry.get("gateway.cache.requests").tag("result", "hit").functionCounter().count())
                .isEqualTo(3.0);
        assertThat(registry.get("gateway.cache.requests").tag("result", "miss").functionCounter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("gateway.ratelimit.rejections").functionCounter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordRequestTimesAndCountsPerClient() {
        GatewayMetrics metrics = metrics();

        metrics.recordRequest(7L, 25);
        metrics.recordRequest(7L, 35);

        assertThat(registry.get("gateway.request.latency").timer().count()).isEqualTo(2);
        assertThat(registry.get("gateway.requests").tag("client", "7").counter().count()).isEqualTo(2.0);
    }

    @Test
    void requestCountsAreSeparatedByClient() {
        GatewayMetrics metrics = metrics();

        metrics.recordRequest(1L, 10);
        metrics.recordRequest(2L, 10);
        metrics.recordRequest(2L, 10);

        assertThat(registry.get("gateway.requests").tag("client", "1").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("gateway.requests").tag("client", "2").counter().count()).isEqualTo(2.0);
    }

    @Test
    void providerCallAndFailoverCountersIncrement() {
        GatewayMetrics metrics = metrics();

        metrics.providerCall();
        metrics.providerCall();
        metrics.failover();

        assertThat(registry.get("gateway.provider.calls").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("gateway.failovers").counter().count()).isEqualTo(1.0);
    }

    @Test
    void budgetRejectionCounterIncrementsPerClient() {
        GatewayMetrics metrics = metrics();

        metrics.budgetRejection(9L);

        assertThat(registry.get("gateway.budget.rejections").tag("client", "9").counter().count())
                .isEqualTo(1.0);
    }
}
