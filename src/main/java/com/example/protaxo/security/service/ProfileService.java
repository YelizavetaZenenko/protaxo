package com.example.protaxo.security.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.security.dto.ProfileFormData;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Current user not found"));
    }

    public void updateProfile(ProfileFormData form) {
        User user = getCurrentUser();
        user.setFullName(form.getFullName());

        boolean changingPassword = form.getNewPassword() != null && !form.getNewPassword().isBlank();
        if (changingPassword) {
            if (form.getCurrentPassword() == null || !passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
                throw new BusinessRuleException("Поточний пароль невірний");
            }
            if (form.getNewPassword().length() < 8) {
                throw new BusinessRuleException("Новий пароль має містити щонайменше 8 символів");
            }
            if (!form.getNewPassword().equals(form.getConfirmNewPassword())) {
                throw new BusinessRuleException("Нові паролі не збігаються");
            }
            user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        }

        userRepository.save(user);
        auditLogService.record(AuditAction.UPDATE, "User", user.getId());
    }
}
