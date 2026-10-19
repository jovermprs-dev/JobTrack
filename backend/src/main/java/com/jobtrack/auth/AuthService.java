package com.jobtrack.auth;

import com.jobtrack.auth.AuthDtos.ChangePasswordRequest;
import com.jobtrack.auth.AuthDtos.RegisterRequest;
import com.jobtrack.common.BadRequestException;
import com.jobtrack.common.ConflictException;
import com.jobtrack.common.NotFoundException;
import com.jobtrack.user.User;
import com.jobtrack.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        requireBcryptCompatible(request.password());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }
        try {
            return users.saveAndFlush(new User(email, request.displayName().strip(), encoder.encode(request.password())));
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual no es correcta.");
        }
        requireBcryptCompatible(request.newPassword());
        user.setPasswordHash(encoder.encode(request.newPassword()));
    }

    /** BCrypt solo usa los primeros 72 bytes: rechazamos en vez de truncar en silencio. */
    private static void requireBcryptCompatible(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("La contraseña no puede superar los 72 bytes.");
        }
    }
}
