package com.jobtrack.demo;

import com.jobtrack.auth.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Modo demo ({@code jobtrack.demo.enabled=true}): una instancia pública de solo lectura, sin cuentas ni inicio de sesión.
 *
 * <ul>
 *   <li>{@link ReadOnlyFilter}: rechaza con 403 cualquier método que no sea de lectura (GET/HEAD/OPTIONS). Es una
 *       denegación por defecto y por método, no por ruta: un endpoint de escritura que se añada en el futuro queda
 *       bloqueado sin tocar nada aquí. Incluye registro, login, logout y cambio de contraseña.</li>
 *   <li>{@link DemoAuthenticationFilter}: autentica cada petición como la cuenta de ejemplo de {@link DemoDataSeeder}.
 *       Sin sesión ni cookie de sesión (política sin estado): nadie "inicia sesión" y no hay nada que robar.</li>
 * </ul>
 *
 * El usuario sigue saliendo del {@code SecurityContext}, nunca del cliente: toda la lógica de pertenencia de datos
 * funciona igual que con una cuenta real.
 */
@Component
@ConditionalOnProperty(name = "jobtrack.demo.enabled", havingValue = "true")
public class DemoModeSecurity {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final DemoDataSeeder seeder;

    public DemoModeSecurity(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    /** Se invoca desde {@code SecurityConfig} antes de construir la cadena de filtros. */
    public void apply(HttpSecurity http) {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Antes que CSRF: una escritura se rechaza aunque lleve un token válido.
                .addFilterBefore(new ReadOnlyFilter(), CsrfFilter.class)
                .addFilterBefore(new DemoAuthenticationFilter(seeder), AnonymousAuthenticationFilter.class);
    }

    static final class ReadOnlyFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (READ_METHODS.contains(request.getMethod())) {
                chain.doFilter(request, response);
                return;
            }
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "Demostración de solo lectura",
                    "Esta es una demostración de solo lectura: no se puede crear, modificar ni eliminar nada.");
        }
    }

    static final class DemoAuthenticationFilter extends OncePerRequestFilter {
        private final DemoDataSeeder seeder;

        DemoAuthenticationFilter(DemoDataSeeder seeder) {
            this.seeder = seeder;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getRequestURI().startsWith("/api/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Optional<AppUserPrincipal> principal = seeder.principal();
            if (principal.isEmpty()) {
                writeProblem(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Demostración no disponible",
                        "Los datos de la demostración se están preparando. Vuelve a intentarlo en unos segundos.");
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal.get(), null,
                    principal.get().getAuthorities()));
            SecurityContextHolder.getContextHolderStrategy().setContext(context);
            chain.doFilter(request, response);
        }
    }

    /** Error en formato RFC 9457, el mismo que usa el resto de la API (solo texto fijo: nada del cliente se refleja). */
    private static void writeProblem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json;charset=UTF-8");
        String body = "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
