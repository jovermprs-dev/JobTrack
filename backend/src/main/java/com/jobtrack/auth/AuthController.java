package com.jobtrack.auth;

import com.jobtrack.auth.AuthDtos.ChangePasswordRequest;
import com.jobtrack.auth.AuthDtos.LoginRequest;
import com.jobtrack.auth.AuthDtos.RegisterRequest;
import com.jobtrack.auth.AuthDtos.UserResponse;
import com.jobtrack.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttempts;
    private final FindByIndexNameSessionRepository<?> sessions;
    private final SecurityContextHolderStrategy contextStrategy = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final boolean demoMode;

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
            LoginAttemptService loginAttempts, FindByIndexNameSessionRepository<?> sessions,
            @Value("${jobtrack.demo.enabled:false}") boolean demoMode) {
        this.demoMode = demoMode;
        this.sessions = sessions;
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        User user = authService.register(request);
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(user.getEmail(), request.password()));
        startSession(auth, http, response);
        return toResponse((AppUserPrincipal) auth.getPrincipal());
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String key = "login|" + email;
        loginAttempts.beforeAttempt(key);
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Credenciales incorrectas");
        }
        loginAttempts.recordSuccess(key);
        startSession(auth, http, response);
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        return toResponse(principal);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return toResponse(principal);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        // La contraseña actual también se protege frente a adivinación desde una sesión secuestrada.
        String key = "password|" + principal.getId();
        loginAttempts.beforeAttempt(key);
        authService.changePassword(principal.getId(), request);
        loginAttempts.recordSuccess(key);
        // Cierra el resto de sesiones de la persona: una cookie robada deja de servir al cambiar la contraseña.
        String current = http.getSession().getId();
        sessions.findByPrincipalName(principal.getUsername()).keySet().stream()
                .filter(id -> !id.equals(current))
                .forEach(sessions::deleteById);
    }

    /** Guarda la autenticación en una sesión nueva (evita fijación de sesión). */
    private void startSession(Authentication auth, HttpServletRequest http, HttpServletResponse response) {
        if (http.getSession(false) != null) {
            http.changeSessionId();
        } else {
            http.getSession(true);
        }
        SecurityContext context = contextStrategy.createEmptyContext();
        context.setAuthentication(auth);
        contextStrategy.setContext(context);
        contextRepository.saveContext(context, http, response);
    }

    private UserResponse toResponse(AppUserPrincipal p) {
        return new UserResponse(p.getId(), p.getEmail(), p.getDisplayName(), demoMode);
    }
}
