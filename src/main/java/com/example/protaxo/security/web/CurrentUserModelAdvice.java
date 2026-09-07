package com.example.protaxo.security.web;

import com.example.protaxo.security.dto.CurrentUserView;
import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;

    @ModelAttribute("currentUser")
    public CurrentUserView currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(u -> new CurrentUserView(u.getFullName(), u.getEmail(), roleLabel(u.getRole()), initials(u.getFullName())))
                .orElse(null);
    }

    private String roleLabel(Role role) {
        return role == Role.ADMIN ? "Адміністратор" : "Майстер";
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
