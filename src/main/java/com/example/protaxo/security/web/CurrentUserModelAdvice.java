package com.example.protaxo.security.web;

import com.example.protaxo.maintenance.service.MaintenanceModeService;
import com.example.protaxo.security.dto.CurrentUserView;
import com.example.protaxo.security.entity.PermissionKey;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import com.example.protaxo.security.service.DeveloperAccounts;
import com.example.protaxo.security.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final MaintenanceModeService maintenanceModeService;
    private final DeveloperAccounts developerAccounts;

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(u -> new CurrentUserView(u.getFullName(), u.getEmail(), roleLabel(u), initials(u.getFullName())))
                .orElse(null);
    }

    // Керують видимістю пунктів "Журнал дій"/"Користувачі" в topbar — раніше було
    // hasRole('ADMIN') прямо в шаблоні, тепер керовано через RolePermissions.
    @ModelAttribute("canViewAuditLog")
    public boolean canViewAuditLog(Authentication authentication) {
        return permissionService.hasPermission(authentication, PermissionKey.CAN_VIEW_AUDIT_LOG);
    }

    /** Смуга «Увімкнено технічні роботи» й перемикач у topbar (бачить лише розробник). */
    @ModelAttribute("maintenanceEnabled")
    public boolean maintenanceEnabled() {
        return maintenanceModeService.isEnabled();
    }

    @ModelAttribute("canManageUsers")
    public boolean canManageUsers(Authentication authentication) {
        return permissionService.hasPermission(authentication, PermissionKey.CAN_MANAGE_USERS);
    }

    private String roleLabel(User user) {
        String label = user.getRole().getLabel();
        return developerAccounts.isDeveloper(user.getEmail()) ? label + " · Розробник" : label;
    }

    private String initials(String fullName) {
        StringBuilder result = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
            }
            if (result.length() >= 2) {
                break;
            }
        }
        return result.isEmpty() ? "?" : result.toString();
    }
}
