package dev.kaloyanyordanov.llmgateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A hand-advanced {@link Clock} for deterministic, zero-flaky rate-limit tests.
 */
final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    void advance(Duration amount) {
        this.instant = this.instant.plus(amount);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId targetZone) {
        return new MutableClock(instant, targetZone);
    }
}
