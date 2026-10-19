package com.jobtrack.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal guardado en la sesión. Su "username" es el id (UUID), no el email: así el nombre
 * de principal que indexa la sesión en BD tiene longitud fija y no cambia si se edita el email.
 * La contraseña se borra tras autenticar para que el hash no se serialice en la sesión.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String displayName;
    private String passwordHash;

    public AppUserPrincipal(UUID id, String email, String displayName, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
