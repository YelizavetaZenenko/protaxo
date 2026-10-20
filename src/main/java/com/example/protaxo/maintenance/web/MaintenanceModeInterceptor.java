package com.example.protaxo.maintenance.web;

import com.example.protaxo.maintenance.service.MaintenanceModeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Поки увімкнено режим технічних робіт, усі запити не-розробників (адміністраторів теж) отримують
 * «Технічні роботи» (503). Розробник ({@code ROLE_DEVELOPER}, див. DeveloperAccounts) працює як
 * звичайно. Шляхи, які мають працювати й під час робіт (вхід, статика, сесійний пульс, публічна
 * перевірка протоколу за QR, Print Agent), винесені з-під перехоплювача у
 * {@code MaintenanceWebConfig}. Див. docs/Технічні роботи.md.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceModeInterceptor implements HandlerInterceptor {

    private final MaintenanceModeService maintenanceModeService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!maintenanceModeService.isEnabled() || request.isUserInRole("DEVELOPER")) {
            return true;
        }
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"Тривають технічні роботи. Спробуйте трохи пізніше.\"}");
            return false;
        }
        request.getRequestDispatcher("/maintenance").forward(request, response);
        return false;
    }
}
