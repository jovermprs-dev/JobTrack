package com.jobtrack.auth;

import com.jobtrack.user.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Carga usuarios por email (el "username" que introduce la persona al iniciar sesión). */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(u -> new AppUserPrincipal(u.getId(), u.getEmail(), u.getDisplayName(), u.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
