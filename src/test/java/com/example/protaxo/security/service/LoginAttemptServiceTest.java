package com.example.protaxo.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    /** Годинник, який тест пересуває вручну. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T09:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptService service =
            new LoginAttemptService(5, Duration.ofMinutes(15), Duration.ofMinutes(15), clock);

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            service.loginFailed(email);
        }
    }

    @Test
    void locksAfterFiveFailuresAndOnlyThatEmail() {
        fail("user@example.com", 4);
        assertThat(service.isLocked("user@example.com")).isFalse();

        service.loginFailed("USER@example.com ");
        assertThat(service.isLocked("user@example.com")).isTrue();
        assertThat(service.minutesLeft("user@example.com")).isEqualTo(15);
        assertThat(service.isLocked("other@example.com")).isFalse();
    }

    @Test
    void unlocksWhenLockoutExpires() {
        fail("user@example.com", 5);
        clock.advance(Duration.ofMinutes(14));
        assertThat(service.isLocked("user@example.com")).isTrue();
        assertThat(service.minutesLeft("user@example.com")).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.isLocked("user@example.com")).isFalse();

        // Після розблокування — знову 5 спроб, а не одразу блок від першої ж помилки.
        service.loginFailed("user@example.com");
        assertThat(service.isLocked("user@example.com")).isFalse();
    }

    @Test
    void failuresOutsideTheWindowDoNotAddUp() {
        fail("user@example.com", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("user@example.com", 4);

        assertThat(service.isLocked("user@example.com")).isFalse();
    }

    @Test
    void successResetsTheCounter() {
        fail("user@example.com", 4);
        service.loginSucceeded("user@example.com");
        fail("user@example.com", 4);

        assertThat(service.isLocked("user@example.com")).isFalse();
    }
}
