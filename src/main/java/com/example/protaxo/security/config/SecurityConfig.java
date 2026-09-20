package com.example.protaxo.security.config;

import com.example.protaxo.security.entity.PermissionKey;
import com.example.protaxo.security.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final PermissionService permissionService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/images/**", "/login", "/accept-invite", "/ws/print-agent", "/verify/**").permitAll()
                        // Не hasRole('ADMIN') напряму — MASTER може отримати доступ через
                        // RolePermissions (Users -> "Права ролі MASTER"), без зміни коду.
                        // Див. PermissionService.
                        .requestMatchers("/audit-log/**").access(requiresPermission(PermissionKey.CAN_VIEW_AUDIT_LOG))
                        .requestMatchers("/users/**").access(requiresPermission(PermissionKey.CAN_MANAGE_USERS))
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/repair-workers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/repair-workers/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/master-cards").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/*/*/delete").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/invoices", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .httpBasic(httpBasic -> {});

        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> requiresPermission(PermissionKey key) {
        return (authentication, context) ->
                new AuthorizationDecision(permissionService.hasPermission(authentication.get(), key));
    }
}
