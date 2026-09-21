package com.example.protaxo.security.listener;

import com.example.protaxo.security.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security publishes {@link AuthenticationSuccessEvent} for every successful
 * authentication (form login, HTTP Basic) regardless of which filter handled it, so this is the
 * one place that needs to know about logins rather than wiring a custom
 * AuthenticationSuccessHandler into {@code SecurityConfig}'s form-login chain.
 */
@Component
@RequiredArgsConstructor
public class LoginTrackingListener {

    private final UserRepository userRepository;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        userRepository.updateLastLoginAt(email, Instant.now());
    }
}
