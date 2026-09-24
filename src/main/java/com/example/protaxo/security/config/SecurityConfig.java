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
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
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
                        .requestMatchers("/css/**", "/images/**", "/login", "/accept-invite", "/forgot-password", "/reset-password", "/ws/print-agent", "/verify/**").permitAll()
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
                        // Фінанси (docs/Фінансовий облік.md): майстер бачить лише підсумки каси
                        // й приймає оплату за нарядом; решта модуля — адміністратор і бухгалтер.
                        .requestMatchers("/finance/cash").hasAnyRole("ADMIN", "ACCOUNTANT", "MASTER")
                        .requestMatchers(HttpMethod.POST, "/finance/payments").hasAnyRole("ADMIN", "ACCOUNTANT", "MASTER")
                        .requestMatchers("/finance/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        // Ревізія складу (docs/Ревізія складу.md) — змінює залишки в каталозі.
                        .requestMatchers("/stock-revisions", "/stock-revisions/**").hasAnyRole("ADMIN", "ACCOUNTANT")
                        // Бухгалтер — окремий акаунт "лише бухгалтерія": перегляд наряду з оплатами
                        // та його PDF, каталог (ціна/залишок/ПДВ, див. CatalogItemService), профіль.
                        // Усе інше для нього закрито переліком нижче через anyRequest.
                        .requestMatchers("/invoices/new").hasAnyRole("ADMIN", "MASTER")
                        .requestMatchers(HttpMethod.GET, "/invoices/*", "/invoices/*/bill-pdf", "/invoices/*/act-pdf").authenticated()
                        .requestMatchers(HttpMethod.GET, "/catalog-items", "/catalog-items/*", "/catalog-items/*/edit").authenticated()
                        .requestMatchers(HttpMethod.POST, "/catalog-items/*/edit").authenticated()
                        .requestMatchers("/profile", "/profile/**", "/", "/error", "/session/**").authenticated()
                        .anyRequest().hasAnyRole("ADMIN", "MASTER"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(roleLandingHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .httpBasic(httpBasic -> {});

        return http.build();
    }

    /** Після входу: бухгалтер — одразу в панель фінансів, решта — у наряд-закази, як і раніше. */
    private AuthenticationSuccessHandler roleLandingHandler() {
        return (request, response, authentication) -> {
            boolean accountant = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ACCOUNTANT"));
            response.sendRedirect(request.getContextPath() + (accountant ? "/finance" : "/invoices"));
        };
    }

    private AuthorizationManager<RequestAuthorizationContext> requiresPermission(PermissionKey key) {
        return (authentication, context) ->
                new AuthorizationDecision(permissionService.hasPermission(authentication.get(), key));
    }
}
