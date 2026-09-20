package com.example.protaxo.security.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.security.entity.PasswordResetToken;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.PasswordResetTokenRepository;
import com.example.protaxo.security.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailService passwordResetMailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    /**
     * Silently no-ops for an unknown or inactive email — the controller shows the same
     * "check your inbox" message either way, so this never reveals which emails exist.
     */
    public void requestReset(String email) {
        userRepository.findByEmail(email)
                .filter(User::isActive)
                .ifPresent(user -> {
                    String token = UUID.randomUUID().toString();
                    PasswordResetToken resetToken = PasswordResetToken.builder()
                            .user(user)
                            .token(token)
                            .expiresAt(Instant.now().plus(TOKEN_TTL))
                            .createdAt(Instant.now())
                            .build();
                    passwordResetTokenRepository.save(resetToken);
                    passwordResetMailService.sendPasswordReset(user.getEmail(), user.getFullName(), token);
                });
    }

    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return passwordResetTokenRepository.findByToken(token)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    public void resetPassword(String token, String rawPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessRuleException("Посилання недійсне"));
        if (!resetToken.isValid()) {
            throw new BusinessRuleException("Посилання протерміноване або вже використане");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);

        auditLogService.record(AuditAction.UPDATE, "User", user.getId());
    }
}
