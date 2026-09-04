package dev.kaloyanyordanov.llmgateway.metrics;

import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * Tracks recent request latencies and computes p50/p95/p99 deterministically via
 * the nearest-rank method over a bounded sliding window. Bounded storage keeps
 * memory flat; nearest-rank (rather than an approximate histogram) makes the
 * percentiles exact for a given set of samples, so they are precisely unit
 * testable. All access is synchronized.
 */
@Component
public class LatencyTracker {

    /** Number of most-recent samples the percentiles are computed over. */
    static final int WINDOW = 1024;

    private final long[] samples = new long[WINDOW];
    private int count;
    private int next;

    /**
     * Records one request's latency.
     *
     * @param millis the request duration in milliseconds
     */
    public synchronized void record(long millis) {
        samples[next] = millis;
        next = (next + 1) % WINDOW;
        if (count < WINDOW) {
            count++;
        }
    }

    /**
     * @return the current p50/p95/p99 over the window (all zero if no samples yet)
     */
    public synchronized LatencyPercentiles snapshot() {
        if (count == 0) {
            return new LatencyPercentiles(0, 0, 0, 0);
        }
        long[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        return new LatencyPercentiles(
                percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99), count);
    }

    private static long percentile(long[] sorted, int p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.length);
        int index = Math.min(sorted.length - 1, Math.max(0, rank - 1));
        return sorted[index];
    }
}
