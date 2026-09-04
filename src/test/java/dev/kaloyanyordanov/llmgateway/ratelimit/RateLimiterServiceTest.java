package dev.kaloyanyordanov.llmgateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private RateLimiterService serviceWithCapacity(long capacity, MutableClock clock) {
        return new RateLimiterService(
                new RateLimiterProperties(capacity, Duration.ofMinutes(1)), clock);
    }

    @Test
    void enforcesCapacityPerClient() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(2, clock);

        assertThat(service.tryAcquire(1L, null)).isTrue();
        assertThat(service.tryAcquire(1L, null)).isTrue();
        assertThat(service.tryAcquire(1L, null)).isFalse();
    }

    @Test
    void countsRejectionsWhenOverLimit() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(1, clock);

        assertThat(service.tryAcquire(1L, null)).isTrue();
        assertThat(service.tryAcquire(1L, null)).isFalse();
        assertThat(service.tryAcquire(1L, null)).isFalse();

        assertThat(service.rejectionCount()).isEqualTo(2);
    }

    @Test
    void clientsAreIsolatedFromEachOther() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(1, clock);

        assertThat(service.tryAcquire(1L, null)).isTrue();
        assertThat(service.tryAcquire(1L, null)).isFalse();
        // A different client has its own full bucket.
        assertThat(service.tryAcquire(2L, null)).isTrue();
    }

    @Test
    void reusesTheSameBucketAcrossCallsForOneClient() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(60, clock);

        for (int i = 0; i < 60; i++) {
            assertThat(service.tryAcquire(7L, null)).isTrue();
        }
        assertThat(service.tryAcquire(7L, null)).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.tryAcquire(7L, null)).isTrue();
    }

    @Test
    void perClientLimitOverridesTheGlobalDefault() {
        MutableClock clock = new MutableClock(START);
        // Global default is high; the client's own lower limit must win.
        RateLimiterService service = serviceWithCapacity(60, clock);

        assertThat(service.tryAcquire(1L, 2)).isTrue();
        assertThat(service.tryAcquire(1L, 2)).isTrue();
        assertThat(service.tryAcquire(1L, 2)).isFalse();
    }

    @Test
    void distinctClientsEnforceDistinctLimitsIndependently() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(60, clock);

        // Client 1 has a limit of 1, client 2 a limit of 3.
        assertThat(service.tryAcquire(1L, 1)).isTrue();
        assertThat(service.tryAcquire(1L, 1)).isFalse();

        assertThat(service.tryAcquire(2L, 3)).isTrue();
        assertThat(service.tryAcquire(2L, 3)).isTrue();
        assertThat(service.tryAcquire(2L, 3)).isTrue();
        assertThat(service.tryAcquire(2L, 3)).isFalse();
    }

    @Test
    void changingAClientsLimitRebuildsItsBucketAtTheNewCapacity() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(60, clock);

        // Exhaust a small limit.
        assertThat(service.tryAcquire(1L, 1)).isTrue();
        assertThat(service.tryAcquire(1L, 1)).isFalse();

        // Raising the limit rebuilds the bucket full at the new capacity.
        assertThat(service.tryAcquire(1L, 5)).isTrue();
        assertThat(service.tryAcquire(1L, 5)).isTrue();
    }

    @Test
    void anUnchangedLimitKeepsTheSameBucketRatherThanRebuilding() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(60, clock);

        // With a stable limit the bucket is reused, so consumption accumulates
        // rather than resetting on each call.
        assertThat(service.tryAcquire(1L, 2)).isTrue();
        assertThat(service.tryAcquire(1L, 2)).isTrue();
        assertThat(service.tryAcquire(1L, 2)).isFalse();
    }
}
