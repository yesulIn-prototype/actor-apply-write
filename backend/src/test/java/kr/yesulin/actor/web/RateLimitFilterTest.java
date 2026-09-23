package kr.yesulin.actor.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimitFilterTest {
    @Test
    void limitsEachVisitorWithinTheWindowOnly() {
        MovableClock clock = new MovableClock(Instant.parse("2026-09-23T00:00:00Z"));
        RateLimitFilter filter = new RateLimitFilter(2, Duration.ofMinutes(10), clock);

        assertThat(filter.allow("1.1.1.1")).isTrue();
        assertThat(filter.allow("1.1.1.1")).isTrue();
        assertThat(filter.allow("1.1.1.1")).as("third build in the window").isFalse();
        assertThat(filter.allow("2.2.2.2")).as("another visitor is unaffected").isTrue();

        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertThat(filter.allow("1.1.1.1")).as("window passed").isTrue();
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
