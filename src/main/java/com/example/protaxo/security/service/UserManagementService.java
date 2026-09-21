package com.example.protaxo.security.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.common.exception.BusinessRuleException;
import com.example.protaxo.common.exception.NotFoundException;
import com.example.protaxo.security.dto.UserEditFormData;
import com.example.protaxo.security.dto.UserInviteFormData;
import com.example.protaxo.security.dto.UserSummary;
import com.example.protaxo.security.entity.InvitationToken;
import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.InvitationTokenRepository;
import com.example.protaxo.security.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final InvitationTokenRepository invitationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvitationMailService invitationMailService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<UserSummary> findAll() {
        return userRepository.findAll().stream()
                .map(u -> new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive(), u.getLastLoginAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserSummary findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User %d not found".formatted(id)));
        return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive(), user.getLastLoginAt());
    }

    public void invite(UserInviteFormData form) {
        if (userRepository.findByEmail(form.getEmail()).isPresent()) {
            throw new BusinessRuleException("Користувач з такою поштою вже існує");
        }
        requireAdminToAssign(form.getRole());

        User user = new User();
        user.setFullName(form.getFullName());
        user.setEmail(form.getEmail());
        user.setRole(form.getRole());
        user.setActive(false);
        // Unguessable placeholder — overwritten once the invite is accepted; the account
        // cannot log in before that anyway because isActive is false.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        User saved = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        InvitationToken invitationToken = InvitationToken.builder()
                .user(saved)
                .token(token)
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .createdAt(Instant.now())
                .build();
        invitationTokenRepository.save(invitationToken);

        invitationMailService.sendInvitation(saved.getEmail(), saved.getFullName(), token);
        auditLogService.record(AuditAction.CREATE, "User", saved.getId());
    }

    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return invitationTokenRepository.findByToken(token)
                .map(InvitationToken::isValid)
                .orElse(false);
    }

    public void acceptInvite(String token, String rawPassword) {
        InvitationToken invitationToken = invitationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessRuleException("Посилання недійсне"));
        if (!invitationToken.isValid()) {
            throw new BusinessRuleException("Посилання протерміноване або вже використане");
        }

        User user = invitationToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setActive(true);
        userRepository.save(user);

        invitationToken.setUsedAt(Instant.now());
        invitationTokenRepository.save(invitationToken);

        auditLogService.record(AuditAction.UPDATE, "User", user.getId());
    }

    public void update(Long id, UserEditFormData form) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User %d not found".formatted(id)));

        userRepository.findByEmail(form.getEmail())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessRuleException("Користувач з такою поштою вже існує");
                });
        requireAdminToAssign(form.getRole());

        if (target.getRole() == Role.ADMIN && form.getRole() != Role.ADMIN
                && userRepository.findByRoleAndActiveTrueForUpdate(Role.ADMIN).size() <= 1) {
            throw new BusinessRuleException("Не можна зняти роль адміністратора з останнього адміністратора");
        }

        target.setFullName(form.getFullName());
        target.setEmail(form.getEmail());
        target.setRole(form.getRole());
        userRepository.save(target);
        auditLogService.record(AuditAction.UPDATE, "User", id);
    }

    public void delete(Long id) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User %d not found".formatted(id)));

        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (target.getEmail().equals(currentEmail)) {
            throw new BusinessRuleException("Не можна видалити власний акаунт");
        }
        if (target.getRole() == Role.ADMIN && userRepository.findByRoleAndActiveTrueForUpdate(Role.ADMIN).size() <= 1) {
            throw new BusinessRuleException("Не можна видалити останнього адміністратора");
        }

        target.setDeletedAt(Instant.now());
        userRepository.save(target);
        auditLogService.record(AuditAction.DELETE, "User", id);
    }

    /**
     * {@code CAN_MANAGE_USERS} lets MASTER reach {@code /users} at all (see RolePermissions), but
     * that permission is meant to cover managing the user list, not minting new ADMIN accounts —
     * without this check a MASTER with the toggle on could set their own (or anyone's) {@code role}
     * to ADMIN via a normal invite/edit POST, a full privilege escalation the permission toggle was
     * never meant to grant. Only an actual ROLE_ADMIN authority may assign the ADMIN role.
     */
    private void requireAdminToAssign(Role role) {
        if (role != Role.ADMIN) {
            return;
        }
        boolean callerIsAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!callerIsAdmin) {
            throw new BusinessRuleException("Лише адміністратор може призначити роль адміністратора");
        }
    }
}
