package com.example.protaxo.maintenance.web;

import com.example.protaxo.maintenance.service.MaintenanceModeService;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Сторінка «Технічні роботи» і перемикач режиму для розробника (docs/Технічні роботи.md). */
@Controller
@RequestMapping("/maintenance")
@RequiredArgsConstructor
public class MaintenancePageController {

    private final MaintenanceModeService maintenanceModeService;

    /**
     * Сюди {@link MaintenanceModeInterceptor} перенаправляє (forward) усі запити не-розробників.
     * Відкрита напряму, коли робіт немає, — веде на головну; розробник при увімкненому режимі
     * бачить її як попередній перегляд. Будь-який HTTP-метод: перехоплювач пересилає сюди й POST-форми.
     */
    @RequestMapping
    public String page(HttpServletResponse response) {
        if (!maintenanceModeService.isEnabled()) {
            return "redirect:/";
        }
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setHeader("Retry-After", "300");
        return "maintenance";
    }

    /** Кнопка в меню користувача (лише розробник — SecurityConfig); повертає туди, звідки натиснули. */
    @PostMapping("/toggle")
    public String toggle(@RequestParam boolean enabled, Authentication authentication,
                         @RequestHeader(value = "Referer", required = false) String referer) {
        maintenanceModeService.setEnabled(enabled, authentication.getName());
        return "redirect:" + localPath(referer);
    }

    /** Лише шлях і query з Referer — щоб редирект не міг вести на інший сайт. */
    static String localPath(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "/";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
