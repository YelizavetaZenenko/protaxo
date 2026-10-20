package com.example.protaxo.maintenance.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class MaintenanceWebConfig implements WebMvcConfigurer {

    private final MaintenanceModeInterceptor maintenanceModeInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(maintenanceModeInterceptor)
                .excludePathPatterns(
                        "/maintenance", "/maintenance/**",
                        "/login", "/error", "/session/**",
                        "/css/**", "/images/**", "/favicon.ico",
                        // Інспектор сканує QR на наклейці — перевірка протоколу працює й під час робіт.
                        "/verify/**",
                        "/ws/print-agent");
    }
}
