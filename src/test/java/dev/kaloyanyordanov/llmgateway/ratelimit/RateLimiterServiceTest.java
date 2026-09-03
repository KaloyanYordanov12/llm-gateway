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

        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isFalse();
    }

    @Test
    void clientsAreIsolatedFromEachOther() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(1, clock);

        assertThat(service.tryAcquire(1L)).isTrue();
        assertThat(service.tryAcquire(1L)).isFalse();
        // A different client has its own full bucket.
        assertThat(service.tryAcquire(2L)).isTrue();
    }

    @Test
    void reusesTheSameBucketAcrossCallsForOneClient() {
        MutableClock clock = new MutableClock(START);
        RateLimiterService service = serviceWithCapacity(60, clock);

        for (int i = 0; i < 60; i++) {
            assertThat(service.tryAcquire(7L)).isTrue();
        }
        assertThat(service.tryAcquire(7L)).isFalse();

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.tryAcquire(7L)).isTrue();
    }
}
