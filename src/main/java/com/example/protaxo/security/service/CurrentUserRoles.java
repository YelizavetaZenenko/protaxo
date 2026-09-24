package com.example.protaxo.security.service;

import com.example.protaxo.security.entity.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Перевірка ролі поточного користувача з SecurityContext — для правил у сервісах, не в URL-матчерах. */
public final class CurrentUserRoles {

    private CurrentUserRoles() {
    }

    public static boolean has(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    public static String username() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
