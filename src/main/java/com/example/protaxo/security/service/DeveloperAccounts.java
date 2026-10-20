package com.example.protaxo.security.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Акаунти розробника — з {@code app.developer-emails} (на сервері {@code APP_DEVELOPER_EMAILS} у .env).
 * Розробник — не окрема роль, а право {@code ROLE_DEVELOPER} поверх звичайної ролі (зазвичай ADMIN):
 * лише він вмикає технічні роботи й заходить на сайт, поки вони тривають. Див. docs/Технічні роботи.md.
 */
@Component
public class DeveloperAccounts {

    public static final String AUTHORITY = "ROLE_DEVELOPER";

    private final Set<String> emails;

    public DeveloperAccounts(@Value("${app.developer-emails:}") String developerEmails) {
        this.emails = Arrays.stream(developerEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isDeveloper(String email) {
        return email != null && emails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
