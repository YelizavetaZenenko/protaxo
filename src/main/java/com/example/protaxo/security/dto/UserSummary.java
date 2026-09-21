package com.example.protaxo.security.dto;

import com.example.protaxo.security.entity.Role;
import java.time.Instant;

public record UserSummary(Long id, String fullName, String email, Role role, boolean active, Instant lastLoginAt) {
}
