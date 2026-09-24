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

/**
 * Вихід із системи після закриття всіх вкладок сайту (див. docs/Безпека і ролі.md).
 *
 * <ul>
 *   <li>{@code GET /session/ping} — пульс відкритої вкладки (topbar.html, раз на хвилину): тримає
 *       коротку серверну сесію живою, поки відкрита хоч одна вкладка;</li>
 *   <li>{@code POST /session/closing} — {@code sendBeacon} з останньої вкладки при її закритті:
 *       сесії лишається {@code app.session.closing-grace} (20 с). Якщо це був лише перехід на іншу
 *       сторінку чи F5, наступний же запит поверне звичайний таймаут — див. SessionTimeoutResetFilter.</li>
 * </ul>
 */
@Controller
@RequestMapping("/session")
public class SessionLifecycleController {

    private final Duration closingGrace;

    public SessionLifecycleController(@Value("${app.session.closing-grace:20s}") Duration closingGrace) {
        this.closingGrace = closingGrace;
    }

    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/closing")
    public ResponseEntity<Void> closing(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setMaxInactiveInterval((int) closingGrace.toSeconds());
        }
        return ResponseEntity.noContent().build();
    }
}
