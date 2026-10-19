package com.jobtrack.auth;

import com.jobtrack.common.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Limita los intentos de autenticación por clave (email en el login; usuario en el cambio de contraseña).
 *
 * <p>El intento se contabiliza <b>antes</b> de comprobar la contraseña y de forma atómica, de modo que las peticiones
 * concurrentes no superan el límite. La clave no incluye la IP: las cabeceras de reenvío pueden falsificarse y permitirían
 * esquivar el límite. Contrapartida: quien conozca un email puede bloquearlo temporalmente (ver docs/security-and-config.md).
 * El estado vive en memoria con tamaño acotado (LRU): válido para una instancia.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private record Attempts(int count, Instant windowStart) {
    }

    private final Map<String, Attempts> attempts = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Attempts> eldest) {
            return size() > MAX_TRACKED_KEYS;
        }
    };
    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;

    public LoginAttemptService(Clock clock,
            @Value("${jobtrack.auth.max-failed-logins}") int maxAttempts,
            @Value("${jobtrack.auth.lockout-minutes}") long lockoutMinutes) {
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(lockoutMinutes);
    }

    /** Registra un intento o lanza 429 si la clave ya agotó los suyos en la ventana actual. */
    public synchronized void beforeAttempt(String key) {
        Instant now = clock.instant();
        Attempts current = attempts.get(key);
        if (current == null || !now.isBefore(current.windowStart().plus(window))) {
            attempts.put(key, new Attempts(1, now));
            return;
        }
        if (current.count() >= maxAttempts) {
            long retry = Duration.between(now, current.windowStart().plus(window)).toSeconds() + 1;
            throw new TooManyAttemptsException("Demasiados intentos fallidos. Inténtalo de nuevo más tarde.", retry);
        }
        attempts.put(key, new Attempts(current.count() + 1, current.windowStart()));
    }

    public synchronized void recordSuccess(String key) {
        attempts.remove(key);
    }
}
