package com.jobtrack.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "El email es obligatorio") @Email(message = "El email no es válido")
            @Size(max = 254, message = "El email es demasiado largo") String email,
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres") String password,
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre es demasiado largo") String displayName) {
    }

    public record LoginRequest(
            @NotBlank(message = "El email es obligatorio") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "La contraseña actual es obligatoria") String currentPassword,
            @NotBlank(message = "La nueva contraseña es obligatoria")
            @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres") String newPassword) {
    }

    public record UserResponse(UUID id, String email, String displayName) {
    }
}
