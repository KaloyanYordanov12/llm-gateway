package dev.kaloyanyordanov.llmgateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A single client's token bucket. Starts full and refills continuously at a rate
 * of {@code capacity} tokens per {@code refillPeriod}, driven by an injected
 * {@link Clock} so refill behaviour is fully deterministic in tests.
 *
 * <p>Instances are thread-safe: {@link #tryConsume()} is synchronized.</p>
 */
public class TokenBucket {

    private final long capacity;
    private final double tokensPerNano;
    private final Clock clock;

    private double tokens;
    private Instant lastRefill;

    /**
     * Creates a full bucket.
     *
     * @param capacity     maximum tokens (also the burst size)
     * @param refillPeriod time to refill a full {@code capacity} of tokens
     * @param clock        time source
     */
    public TokenBucket(long capacity, Duration refillPeriod, Clock clock) {
        this.capacity = capacity;
        this.tokensPerNano = (double) capacity / refillPeriod.toNanos();
        this.clock = clock;
        this.tokens = capacity;
        this.lastRefill = clock.instant();
    }

    /**
     * Attempts to consume a single token.
     *
     * @return {@code true} if a token was available and consumed; {@code false}
     *     if the bucket is empty (request should be rate-limited)
     */
    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * @return this bucket's capacity (also its burst size); used to detect when a
     *     client's configured limit has changed and the bucket must be rebuilt
     */
    public long capacity() {
        return capacity;
    }

    private void refill() {
        Instant now = clock.instant();
        long elapsedNanos = Duration.between(lastRefill, now).toNanos();
        if (elapsedNanos <= 0) {
            return;
        }
        double refilled = elapsedNanos * tokensPerNano;
        tokens = Math.min(capacity, tokens + refilled);
        lastRefill = now;
    }
}
