package dev.kaloyanyordanov.llmgateway.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.ratelimit.RateLimiterService;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import dev.kaloyanyordanov.llmgateway.usage.UsageTotals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

    private final UsageService usageService = mock(UsageService.class);
    private final ResponseCacheService cache = mock(ResponseCacheService.class);
    private final RateLimiterService rateLimiter = mock(RateLimiterService.class);
    private final StatsService service = new StatsService(usageService, cache, rateLimiter);

    @Test
    void aggregatesUsageCacheAndRejectionCounters() {
        when(usageService.overallTotals())
                .thenReturn(new UsageTotals(null, 100, 40, new BigDecimal("1.50"), 3));
        when(cache.hitCount()).thenReturn(7L);
        when(cache.missCount()).thenReturn(2L);
        when(rateLimiter.rejectionCount()).thenReturn(1L);

        StatsView stats = service.currentStats();

        assertThat(stats.totalRequests()).isEqualTo(3);
        assertThat(stats.totalInputTokens()).isEqualTo(100);
        assertThat(stats.totalOutputTokens()).isEqualTo(40);
        assertThat(stats.totalCost()).isEqualByComparingTo("1.50");
        assertThat(stats.cacheHits()).isEqualTo(7);
        assertThat(stats.cacheMisses()).isEqualTo(2);
        assertThat(stats.rateLimitRejections()).isEqualTo(1);
    }
}
