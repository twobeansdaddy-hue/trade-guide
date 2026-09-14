package com.tradeguide.service.market;

import com.tradeguide.exception.MarketDataRateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwelveDataRateLimitTrackerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void allowsCallsWhenNeverRateLimited() {
        TwelveDataRateLimitTracker tracker = new TwelveDataRateLimitTracker(clock, 30);

        assertThatCode(tracker::requireNotCoolingDown).doesNotThrowAnyException();
    }

    @Test
    void blocksSubsequentCallsUntilCooldownElapses() {
        TwelveDataRateLimitTracker tracker = new TwelveDataRateLimitTracker(clock, 30);

        tracker.recordRateLimited(null);

        assertThatThrownBy(tracker::requireNotCoolingDown)
                .isInstanceOf(MarketDataRateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(31));

        assertThatCode(tracker::requireNotCoolingDown).doesNotThrowAnyException();
    }

    @Test
    void usesRetryAfterDurationWhenProvided() {
        TwelveDataRateLimitTracker tracker = new TwelveDataRateLimitTracker(clock, 30);

        tracker.recordRateLimited(Duration.ofSeconds(5));

        clock.advance(Duration.ofSeconds(6));

        assertThatCode(tracker::requireNotCoolingDown).doesNotThrowAnyException();
    }

    @Test
    void tracksUsageAcrossSuccessfulCalls() {
        TwelveDataRateLimitTracker tracker = new TwelveDataRateLimitTracker(clock, 30);

        tracker.recordSuccess(3);
        tracker.recordSuccess(2);

        TwelveDataRateLimitTracker.Snapshot snapshot = tracker.snapshot();
        assertThat(snapshot.totalRequestCount()).isEqualTo(2);
        assertThat(snapshot.totalSymbolCreditsUsed()).isEqualTo(5);
        assertThat(snapshot.rateLimitHitCount()).isZero();
    }

    @Test
    void tracksRateLimitHitCountAndLastRateLimitAt() {
        TwelveDataRateLimitTracker tracker = new TwelveDataRateLimitTracker(clock, 30);

        tracker.recordRateLimited(null);

        TwelveDataRateLimitTracker.Snapshot snapshot = tracker.snapshot();
        assertThat(snapshot.rateLimitHitCount()).isEqualTo(1);
        assertThat(snapshot.lastRateLimitAt()).isEqualTo(clock.instant());
        assertThat(snapshot.cooldownUntil()).isAfter(clock.instant());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
