package dev.kaloyanyordanov.llmgateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LatencyTrackerTest {

    @Test
    void emptyTrackerReportsZeros() {
        LatencyPercentiles p = new LatencyTracker().snapshot();

        assertThat(p.p50Millis()).isZero();
        assertThat(p.p95Millis()).isZero();
        assertThat(p.p99Millis()).isZero();
        assertThat(p.count()).isZero();
    }

    @Test
    void computesNearestRankPercentilesFromKnownInputs() {
        LatencyTracker tracker = new LatencyTracker();
        // 1..100 => nearest-rank p50=50, p95=95, p99=99.
        for (int i = 1; i <= 100; i++) {
            tracker.record(i);
        }

        LatencyPercentiles p = tracker.snapshot();

        assertThat(p.count()).isEqualTo(100);
        assertThat(p.p50Millis()).isEqualTo(50);
        assertThat(p.p95Millis()).isEqualTo(95);
        assertThat(p.p99Millis()).isEqualTo(99);
    }

    @Test
    void percentilesAreOrderIndependent() {
        LatencyTracker tracker = new LatencyTracker();
        // Same set, inserted descending — sorting makes the result identical.
        for (int i = 100; i >= 1; i--) {
            tracker.record(i);
        }

        LatencyPercentiles p = tracker.snapshot();

        assertThat(p.p50Millis()).isEqualTo(50);
        assertThat(p.p99Millis()).isEqualTo(99);
    }

    @Test
    void singleSampleIsEveryPercentile() {
        LatencyTracker tracker = new LatencyTracker();
        tracker.record(42);

        LatencyPercentiles p = tracker.snapshot();

        assertThat(p.count()).isEqualTo(1);
        assertThat(p.p50Millis()).isEqualTo(42);
        assertThat(p.p95Millis()).isEqualTo(42);
        assertThat(p.p99Millis()).isEqualTo(42);
    }

    @Test
    void windowIsBoundedAndEvictsOldSamples() {
        LatencyTracker tracker = new LatencyTracker();
        // Fill the window with a high value, then overwrite it entirely with a low one.
        for (int i = 0; i < LatencyTracker.WINDOW; i++) {
            tracker.record(1000);
        }
        for (int i = 0; i < LatencyTracker.WINDOW; i++) {
            tracker.record(1);
        }

        LatencyPercentiles p = tracker.snapshot();

        // Count never exceeds the window, and only the recent (low) samples remain.
        assertThat(p.count()).isEqualTo(LatencyTracker.WINDOW);
        assertThat(p.p50Millis()).isEqualTo(1);
        assertThat(p.p99Millis()).isEqualTo(1);
    }
}
