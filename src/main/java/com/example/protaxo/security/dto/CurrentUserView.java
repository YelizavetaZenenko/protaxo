package com.example.protaxo.security.dto;

public record CurrentUserView(String fullName, String email, String roleLabel, String initials) {
}
