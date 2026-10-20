package com.example.protaxo.security.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Service;

/**
 * Обмеження підбору пароля (docs/Безпека і ролі.md): після {@code app.login.max-failures} невдалих
 * спроб для одного email протягом {@code app.login.window} вхід для нього блокується на
 * {@code app.login.lockout} — навіть із правильним паролем ({@code CustomUserDetailsService} віддає
 * акаунт як locked). Успішний вхід обнуляє лічильник. Ключ — email (без урахування регістру), бо
 * сайт доступний лише в Tailscale і всі запити приходять з кількох відомих адрес. Стан у пам'яті:
 * після перезапуску застосунку лічильники починаються спочатку — для захисту від перебору цього
 * достатньо.
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(@Value("${app.login.max-failures:5}") int maxFailures,
                               @Value("${app.login.window:15m}") Duration window,
                               @Value("${app.login.lockout:15m}") Duration lockout) {
        this(maxFailures, window, lockout, Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration window, Duration lockout, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockout = lockout;
        this.clock = clock;
    }

    public boolean isLocked(String email) {
        Attempts a = attempts.get(key(email));
        return a != null && a.lockedUntil != null && clock.instant().isBefore(a.lockedUntil);
    }

    /** Лише невірний пароль/email — спроба вже заблокованого акаунта лічильник не подовжує. */
    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        loginFailed(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        loginSucceeded(event.getAuthentication().getName());
    }

    public void loginFailed(String email) {
        Instant now = clock.instant();
        attempts.compute(key(email), (k, a) -> {
            if (a == null || a.windowStart.plus(window).isBefore(now)
                    || (a.lockedUntil != null && !now.isBefore(a.lockedUntil))) {
                a = new Attempts(now);
            }
            a.failures++;
            if (a.failures >= maxFailures) {
                a.lockedUntil = now.plus(lockout);
            }
            return a;
        });
    }

    public void loginSucceeded(String email) {
        attempts.remove(key(email));
    }

    /** Хвилини до розблокування, щонайменше 1 — для повідомлення на сторінці входу. */
    public long minutesLeft(String email) {
        Attempts a = attempts.get(key(email));
        if (a == null || a.lockedUntil == null) {
            return 0;
        }
        long seconds = Duration.between(clock.instant(), a.lockedUntil).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        final Instant windowStart;
        int failures;
        Instant lockedUntil;

        Attempts(Instant windowStart) {
            this.windowStart = windowStart;
        }
    }
}
