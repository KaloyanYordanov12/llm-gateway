package dev.kaloyanyordanov.llmgateway.management;

import java.math.BigDecimal;

/**
 * Aggregate gateway statistics for the dashboard.
 *
 * @param totalRequests       billable requests recorded
 * @param totalInputTokens    summed prompt tokens
 * @param totalOutputTokens   summed completion tokens
 * @param totalCost           summed cost
 * @param cacheHits           cache hits since start
 * @param cacheMisses         cache misses since start
 * @param rateLimitRejections requests rejected for exceeding the rate limit
 * @param p50Millis           median request latency (ms) over the recent window
 * @param p95Millis           95th-percentile request latency (ms)
 * @param p99Millis           99th-percentile request latency (ms)
 */
public record StatsView(
        long totalRequests,
        long totalInputTokens,
        long totalOutputTokens,
        BigDecimal totalCost,
        long cacheHits,
        long cacheMisses,
        long rateLimitRejections,
        long p50Millis,
        long p95Millis,
        long p99Millis) {
}
