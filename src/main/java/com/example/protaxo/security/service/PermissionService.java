package com.example.protaxo.security.service;

import com.example.protaxo.audit.entity.AuditAction;
import com.example.protaxo.audit.service.AuditLogService;
import com.example.protaxo.security.entity.PermissionKey;
import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.entity.RolePermissions;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.RolePermissionsRepository;
import com.example.protaxo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final RolePermissionsRepository rolePermissionsRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * ADMIN завжди має повний доступ, незалежно від збережених permissions — інакше
     * адмін міг би випадково заблокувати сам собі керування системою через цей же екран.
     */
    public boolean roleHasPermission(Role role, PermissionKey key) {
        if (role == Role.ADMIN) {
            return true;
        }
        return rolePermissionsRepository.findByRole(role)
                .map(rp -> rp.isGranted(key))
                .orElse(false);
    }

    public boolean hasPermission(Authentication authentication, PermissionKey key) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return false;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(User::getRole)
                .map(role -> roleHasPermission(role, key))
                .orElse(false);
    }

    public boolean masterCanViewAuditLog() {
        return roleHasPermission(Role.MASTER, PermissionKey.CAN_VIEW_AUDIT_LOG);
    }

    public boolean masterCanManageUsers() {
        return roleHasPermission(Role.MASTER, PermissionKey.CAN_MANAGE_USERS);
    }

    @Transactional
    public void updateMasterPermissions(boolean canViewAuditLog, boolean canManageUsers) {
        RolePermissions rp = rolePermissionsRepository.findByRole(Role.MASTER)
                .orElseThrow(() -> new IllegalStateException("RolePermissions не знайдено для ролі MASTER"));
        rp.setGranted(PermissionKey.CAN_VIEW_AUDIT_LOG, canViewAuditLog);
        rp.setGranted(PermissionKey.CAN_MANAGE_USERS, canManageUsers);
        rolePermissionsRepository.save(rp);
        auditLogService.record(AuditAction.UPDATE, "RolePermissions", rp.getId());
    }
}
