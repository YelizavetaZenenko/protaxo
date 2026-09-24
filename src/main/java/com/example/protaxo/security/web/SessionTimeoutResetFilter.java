package com.example.protaxo.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Будь-який запит повертає сесії звичайний таймаут ({@code server.servlet.session.timeout}).
 * Потрібно, бо {@code POST /session/closing} (закрилась "остання" вкладка) скорочує сесію до
 * кількох секунд, а насправді це міг бути лише перехід на іншу сторінку чи F5 — тоді наступна
 * сторінка приходить одразу й скасовує скорочення. Сам /session/closing виставляє короткий
 * таймаут уже після цього фільтра, тож для нього скорочення лишається.
 */
@Component
public class SessionTimeoutResetFilter extends OncePerRequestFilter {

    private final int timeoutSeconds;

    public SessionTimeoutResetFilter(@Value("${server.servlet.session.timeout:30m}") Duration timeout) {
        this.timeoutSeconds = (int) timeout.toSeconds();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getMaxInactiveInterval() != timeoutSeconds) {
            session.setMaxInactiveInterval(timeoutSeconds);
        }
        chain.doFilter(request, response);
    }
}
