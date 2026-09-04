package dev.kaloyanyordanov.llmgateway.metrics;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Central Micrometer instrumentation for the gateway. Cache hit/miss and
 * rate-limit rejection counters are bound to the existing in-service counters (so
 * there is a single source of truth); request count, per-request latency, provider
 * calls, failovers, and budget rejections are recorded at their event sites via
 * the methods here. Counters that make sense per tenant are tagged by client id.
 */
@Component
public class GatewayMetrics {

    private static final String CLIENT_TAG = "client";

    private final MeterRegistry registry;
    private final Timer requestLatency;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "MeterRegistry is a Spring-managed singleton; holding the shared "
                    + "reference is intentional DI, not mutable-state exposure.")
    public GatewayMetrics(MeterRegistry registry, ResponseCacheService cache,
            RateLimiterService rateLimiter) {
        this.registry = registry;
        this.requestLatency = Timer.builder("gateway.request.latency")
                .description("End-to-end latency of proxied requests")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        FunctionCounter.builder("gateway.cache.requests", cache, ResponseCacheService::hitCount)
                .description("Response cache lookups by result")
                .tag("result", "hit")
                .register(registry);
        FunctionCounter.builder("gateway.cache.requests", cache, ResponseCacheService::missCount)
                .tag("result", "miss")
                .register(registry);
        FunctionCounter.builder("gateway.ratelimit.rejections", rateLimiter,
                        RateLimiterService::rejectionCount)
                .description("Requests rejected for exceeding the rate limit")
                .register(registry);
    }

    /**
     * Records one proxied request: its latency and a per-client request count.
     *
     * @param clientId the authenticated client's id
     * @param millis   the request duration in milliseconds
     */
    public void recordRequest(long clientId, long millis) {
        requestLatency.record(millis, TimeUnit.MILLISECONDS);
        registry.counter("gateway.requests", CLIENT_TAG, String.valueOf(clientId)).increment();
    }

    /** Counts one upstream provider call (a cache miss that reached a provider). */
    public void providerCall() {
        registry.counter("gateway.provider.calls").increment();
    }

    /** Counts one failover from a provider to the next in the chain. */
    public void failover() {
        registry.counter("gateway.failovers").increment();
    }

    /**
     * Counts one request rejected for reaching the client's budget cap.
     *
     * @param clientId the rejected client's id
     */
    public void budgetRejection(long clientId) {
        registry.counter("gateway.budget.rejections", CLIENT_TAG, String.valueOf(clientId)).increment();
    }
}
