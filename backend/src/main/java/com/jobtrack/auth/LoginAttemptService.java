package com.jobtrack.auth;

import com.jobtrack.common.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Limita intentos fallidos de login por (IP, email). Estado en memoria: suficiente para una sola instancia;
 * con varias réplicas habría que moverlo a un almacén compartido (ver docs/security-and-config.md).
 */
@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private record Attempts(int failures, Instant firstFailure) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptService(Clock clock,
            @Value("${jobtrack.auth.max-failed-logins}") int maxFailures,
            @Value("${jobtrack.auth.lockout-minutes}") long lockoutMinutes) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(lockoutMinutes);
    }

    public void checkAllowed(String key) {
        Attempts current = attempts.get(key);
        if (current == null) {
            return;
        }
        Instant now = clock.instant();
        Instant expires = current.firstFailure().plus(window);
        if (!now.isBefore(expires)) {
            attempts.remove(key);
            return;
        }
        if (current.failures() >= maxFailures) {
            long retry = Duration.between(now, expires).toSeconds() + 1;
            throw new TooManyAttemptsException("Demasiados intentos fallidos. Inténtalo de nuevo más tarde.", retry);
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        if (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.entrySet().removeIf(e -> !now.isBefore(e.getValue().firstFailure().plus(window)));
        }
        attempts.merge(key, new Attempts(1, now), (old, ignored) ->
                !now.isBefore(old.firstFailure().plus(window))
                        ? new Attempts(1, now)
                        : new Attempts(old.failures() + 1, old.firstFailure()));
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }
}
