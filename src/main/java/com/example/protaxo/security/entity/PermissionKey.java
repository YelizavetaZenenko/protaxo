package com.example.protaxo.security.entity;

/**
 * Кожен елемент відповідає одному ключу в {@link RolePermissions#getPermissions()}.
 * Додавання нового права — це новий елемент тут + дефолтне значення в
 * V37__init_role_permissions.sql (чи новій міграції для існуючих рядків), без зміни схеми.
 */
public enum PermissionKey {
    CAN_VIEW_AUDIT_LOG("canViewAuditLog"),
    CAN_MANAGE_USERS("canManageUsers");

    private final String jsonKey;

    PermissionKey(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    public String jsonKey() {
        return jsonKey;
    }
}
