package com.example.protaxo.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Вихід із системи після закриття всіх вкладок сайту (див. docs/Безпека і ролі.md).
 *
 * <ul>
 *   <li>{@code GET /session/ping?start=…} — пульс відкритої сторінки (topbar.html, кожні 15 с з Web
 *       Worker-а, одразу після завантаження). {@code start} — клієнтський час старту сторінки;
 *       сервер пам'ятає найпізніший.</li>
 *   <li>{@code POST /session/closing?t=…} — {@code sendBeacon} з останньої вкладки в момент
 *       {@code pagehide}. Скорочує сесію до {@code app.session.closing-grace}, <b>якщо</b> після
 *       цього моменту ще не стартувала нова сторінка. Перехід на іншу сторінку чи F5 теж дає
 *       pagehide, але нова сторінка стартує пізніше за нього: якщо її пульс прийшов раніше за
 *       beacon — beacon ігнорується, якщо пізніше — SessionTimeoutResetFilter повертає звичайний
 *       таймаут. Порядок доставки запитів неважливий, бо порівнюються мітки одного годинника.</li>
 * </ul>
 */
@Controller
@RequestMapping("/session")
public class SessionLifecycleController {

    static final String LATEST_PAGE_START = SessionLifecycleController.class.getName() + ".latestPageStart";

    private final Duration closingGrace;

    public SessionLifecycleController(@Value("${app.session.closing-grace:10s}") Duration closingGrace) {
        this.closingGrace = closingGrace;
    }

    @GetMapping("/ping")
    public ResponseEntity<Void> ping(@RequestParam(required = false) Long start, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && start != null) {
            Long latest = (Long) session.getAttribute(LATEST_PAGE_START);
            if (latest == null || start > latest) {
                session.setAttribute(LATEST_PAGE_START, start);
            }
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/closing")
    public ResponseEntity<Void> closing(@RequestParam(required = false) Long t, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.noContent().build();
        }
        Long latestStart = (Long) session.getAttribute(LATEST_PAGE_START);
        boolean newerPageAlive = t != null && latestStart != null && latestStart > t;
        if (!newerPageAlive) {
            session.setMaxInactiveInterval((int) closingGrace.toSeconds());
        }
        return ResponseEntity.noContent().build();
    }
}
