package com.example.protaxo.security.listener;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Входи, виходи й невдалі спроби входу в [[Журнал дій (Audit Log)]]. Успішний вхід і вихід
 * викликає {@code SecurityConfig} (form-login success handler і logout handler) — лише
 * інтерактивні входи, не кожен HTTP Basic-запит, інакше журнал тонув би в повторах. Невдалі
 * спроби ловляться подією Spring Security незалежно від способу входу.
 */
@Component
@RequiredArgsConstructor
public class AuthAuditRecorder {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public void loginSucceeded(Authentication authentication) {
        String email = authentication.getName();
        auditLogService.recordAs(email, AuditAction.LOGIN, "User", userIdOf(email), null);
    }

    public void loggedOut(Authentication authentication) {
        if (authentication == null) {
            return;
        }
        String email = authentication.getName();
        auditLogService.recordAs(email, AuditAction.LOGOUT, "User", userIdOf(email), null);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String email = event.getAuthentication().getName();
        String reason = switch (event.getException()) {
            case DisabledException ignored -> "Акаунт неактивний";
            case LockedException ignored -> "Акаунт заблоковано";
            default -> "Невірна пошта або пароль";
        };
        auditLogService.recordAs(email, AuditAction.LOGIN_FAILED, "User", userIdOf(email),
                Map.of("Причина", new String[]{"", reason}));
    }

    private Long userIdOf(String email) {
        return email == null ? null : userRepository.findByEmail(email).map(User::getId).orElse(null);
    }
}
