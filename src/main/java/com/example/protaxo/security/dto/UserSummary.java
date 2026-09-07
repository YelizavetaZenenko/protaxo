package com.example.protaxo.security.dto;

import com.example.protaxo.security.entity.Role;

public record UserSummary(Long id, String fullName, String email, Role role, boolean active) {
}
