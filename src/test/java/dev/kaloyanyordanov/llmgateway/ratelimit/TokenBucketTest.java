package dev.kaloyanyordanov.llmgateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    @Test
    void startsFullAndAllowsExactlyCapacityRequests() {
        MutableClock clock = new MutableClock(START);
        TokenBucket bucket = new TokenBucket(5, ONE_MINUTE, clock);

        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume()).as("request %d", i).isTrue();
        }
        assertThat(bucket.tryConsume()).as("6th request over capacity").isFalse();
    }

    @Test
    void doesNotRefillWhenClockDoesNotAdvance() {
        MutableClock clock = new MutableClock(START);
        TokenBucket bucket = new TokenBucket(2, ONE_MINUTE, clock);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        // Time frozen: no refill.
        assertThat(bucket.tryConsume()).isFalse();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsProportionallyToElapsedTime() {
        MutableClock clock = new MutableClock(START);
        TokenBucket bucket = new TokenBucket(60, ONE_MINUTE, clock);

        for (int i = 0; i < 60; i++) {
            assertThat(bucket.tryConsume()).isTrue();
        }
        assertThat(bucket.tryConsume()).isFalse();

        // 60 tokens / minute => 1 token per second. Advance 1s => exactly 1 token.
        clock.advance(Duration.ofSeconds(1));
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillIsCappedAtCapacity() {
        MutableClock clock = new MutableClock(START);
        TokenBucket bucket = new TokenBucket(3, ONE_MINUTE, clock);

        assertThat(bucket.tryConsume()).isTrue();
        // Advance far longer than the refill period; must not exceed capacity.
        clock.advance(Duration.ofHours(10));

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).as("capped at capacity of 3").isFalse();
    }
}
