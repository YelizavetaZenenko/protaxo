package com.example.protaxo.security.dto;

import lombok.Data;

@Data
public class RolePermissionsFormData {
    private boolean canViewAuditLog;
    private boolean canManageUsers;
}
