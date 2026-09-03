package dev.kaloyanyordanov.llmgateway.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;

/**
 * Hand-advanced Caffeine {@link Ticker} for deterministic TTL-expiry tests.
 */
final class FakeTicker implements Ticker {

    private long nanos;

    @Override
    public long read() {
        return nanos;
    }

    void advance(Duration amount) {
        this.nanos += amount.toNanos();
    }
}
