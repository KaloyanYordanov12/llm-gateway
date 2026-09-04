package dev.kaloyanyordanov.llmgateway.metrics;

/**
 * A snapshot of request-latency percentiles, in milliseconds, over the most
 * recent window of requests.
 *
 * @param p50Millis median latency
 * @param p95Millis 95th-percentile latency
 * @param p99Millis 99th-percentile latency
 * @param count     number of samples the percentiles were computed from
 */
public record LatencyPercentiles(long p50Millis, long p95Millis, long p99Millis, long count) {
}
