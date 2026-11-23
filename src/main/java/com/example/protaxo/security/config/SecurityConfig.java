package com.example.protaxo.security.config;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.security.entity.PermissionKey;
import com.example.protaxo.security.listener.AuthAuditRecorder;
import com.example.protaxo.security.service.LoginAttemptService;
import com.example.protaxo.security.service.PermissionService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final PermissionService permissionService;
    private final AuthAuditRecorder authAuditRecorder;
    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/accept-invite", "/forgot-password", "/reset-password", "/ws/print-agent", "/verify/**").permitAll()
                        // Технічні роботи (docs/Технічні роботи.md): сторінку бачать усі, вмикає — лише розробник (DeveloperAccounts).
                        .requestMatchers("/maintenance").permitAll()
                        .requestMatchers("/maintenance/toggle").hasRole("DEVELOPER")
                        // Не hasRole('ADMIN') напряму — MASTER може отримати доступ через
                        // RolePermissions (Users -> "Права ролі MASTER"), без зміни коду.
                        // Див. PermissionService.
                        .requestMatchers("/audit-log/**").access(requiresPermission(PermissionKey.CAN_VIEW_AUDIT_LOG))
                        .requestMatchers("/users/**").access(requiresPermission(PermissionKey.CAN_MANAGE_USERS))
                        // Записи календаря видаляє і майстер — він же їх і створює (docs/Календар.md).
                        .requestMatchers(HttpMethod.DELETE, "/api/appointments/*").hasAnyRole("ADMIN", "MASTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/repair-workers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/repair-workers/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/master-cards").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/master-cards/*").hasRole("ADMIN")
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
                        .requestMatchers(HttpMethod.GET, "/catalog-items", "/catalog-items/*", "/catalog-items/*/edit", "/catalog-items/usage/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/catalog-items/*/edit").authenticated()
                        .requestMatchers("/profile", "/profile/**", "/", "/error", "/session/**").authenticated()
                        .anyRequest().hasAnyRole("ADMIN", "MASTER"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(roleLandingHandler())
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .exceptionHandling(ex -> ex.accessDeniedHandler(auditedAccessDeniedHandler()))
                .logout(logout -> logout
                        .addLogoutHandler((request, response, authentication) -> authAuditRecorder.loggedOut(authentication))
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        // Без httpBasic: інакше фонові fetch-запити сторінки після завершення сесії отримують
        // 401 + WWW-Authenticate: Basic, і браузер показує власне вікно "Sign in" кожні 15 с
        // (пульс /session/ping). Тепер усім неавтентифікованим — 302 на /login, а topbar.html
        // сам переводить вкладку туди. Див. docs/Безпека і ролі.md.

        return http.build();
    }

    /**
     * Після входу — назад на сторінку, з якої викинуло (сесія завершилась, відкрили закладку без
     * входу), див. docs/Безпека і ролі.md. Звідки: поле {@code continue} форми входу (topbar.html
     * кладе його в /login?expired&continue=…), або запит, який Spring зберіг перед 302 на /login
     * (пряме відкриття сторінки). Якщо нічого немає — як раніше: бухгалтер у фінанси, решта в наряди.
     */
    private AuthenticationSuccessHandler roleLandingHandler() {
        RequestCache requestCache = new HttpSessionRequestCache();
        return (request, response, authentication) -> {
            authAuditRecorder.loginSucceeded(authentication);
            String target = safeReturnPath(request.getParameter("continue"));
            SavedRequest saved = requestCache.getRequest(request, response);
            if (saved != null) {
                if (target == null && "GET".equals(saved.getMethod())) {
                    URI uri = URI.create(saved.getRedirectUrl());
                    target = safeReturnPath(uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : ""));
                }
                requestCache.removeRequest(request, response);
            }
            if (target == null) {
                boolean accountant = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ACCOUNTANT"));
                target = accountant ? "/finance" : "/invoices";
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    /**
     * Лише відносний шлях цього сайту ("/…", не "//host" і не "/\host" — інакше open redirect)
     * і не службові адреси: фоновий пульс /session/ping, сама сторінка входу, /error.
     */
    static String safeReturnPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/") || path.startsWith("//")
                || path.contains("\\") || path.chars().anyMatch(Character::isISOControl)) {
            return null;
        }
        String p = path.toLowerCase();
        if (p.equals("/") || p.startsWith("/login") || p.startsWith("/logout") || p.startsWith("/session")
                || p.startsWith("/error") || p.startsWith("/api/") || p.startsWith("/ws/")
                || p.startsWith("/css/") || p.startsWith("/images/") || p.startsWith("/favicon")) {
            return null;
        }
        return path;
    }

    /**
     * Забагато невдалих спроб (LoginAttemptService) — окреме повідомлення з часом до
     * розблокування; решта помилок — як і раніше, «Невірний email або пароль».
     */
    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String target = "/login?error";
            if (exception instanceof LockedException) {
                target = "/login?locked=" + loginAttemptService.minutesLeft(request.getParameter("username"));
            }
            // Невдала спроба не губить сторінку, на яку треба повернутись після входу.
            String back = safeReturnPath(request.getParameter("continue"));
            if (back != null) {
                target += "&continue=" + URLEncoder.encode(back, StandardCharsets.UTF_8);
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    /**
     * 403 у журнал дій: немає прав на розділ, або CSRF-токен застарів (форму з давно відкритої
     * сторінки відправили вже після нового входу). Далі — звичайна сторінка помилки 403.
     */
    private AccessDeniedHandler auditedAccessDeniedHandler() {
        AccessDeniedHandler delegate = new AccessDeniedHandlerImpl();
        return (request, response, exception) -> {
            try {
                String query = request.getQueryString();
                Map<String, String[]> changes = new LinkedHashMap<>();
                changes.put("Адреса", new String[]{"", request.getMethod() + " " + request.getRequestURI()
                        + (query != null ? "?" + query : "")});
                changes.put("Причина", new String[]{"", exception instanceof CsrfException
                        ? "Застарілий токен форми (CSRF): сторінку відкрито до повторного входу — оновіть її"
                        : "Немає прав на цей розділ"});
                auditLogService.record(AuditAction.ACCESS_DENIED, "Page", null, changes);
            } catch (RuntimeException e) {
                // журнал не повинен ламати відповідь
            }
            delegate.handle(request, response, exception);
        };
    }

    private AuthorizationManager<RequestAuthorizationContext> requiresPermission(PermissionKey key) {
        return (authentication, context) ->
                new AuthorizationDecision(permissionService.hasPermission(authentication.get(), key));
    }
}
