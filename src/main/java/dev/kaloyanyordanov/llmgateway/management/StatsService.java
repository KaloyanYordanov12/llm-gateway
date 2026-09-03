package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterService;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import dev.kaloyanyordanov.llmgateway.usage.UsageTotals;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Service;

/**
 * Assembles the aggregate {@link StatsView} the dashboard reads, combining usage
 * totals with the live cache and rate-limiter counters.
 */
@Service
public class StatsService {

    private final UsageService usageService;
    private final ResponseCacheService cache;
    private final RateLimiterService rateLimiter;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Collaborators are Spring-managed singletons; holding the shared "
                    + "references is intentional DI, not mutable-state exposure.")
    public StatsService(UsageService usageService, ResponseCacheService cache,
            RateLimiterService rateLimiter) {
        this.usageService = usageService;
        this.cache = cache;
        this.rateLimiter = rateLimiter;
    }

    /**
     * @return the current aggregate statistics
     */
    public StatsView currentStats() {
        UsageTotals totals = usageService.overallTotals();
        return new StatsView(
                totals.requestCount(),
                totals.totalInputTokens(),
                totals.totalOutputTokens(),
                totals.totalCost(),
                cache.hitCount(),
                cache.missCount(),
                rateLimiter.rejectionCount());
    }
}
