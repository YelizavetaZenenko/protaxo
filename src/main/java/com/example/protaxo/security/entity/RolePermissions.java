package com.example.protaxo.security.entity;

import com.example.protaxo.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RolePermissions extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Role role;

    @Convert(converter = PermissionsJsonConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private Map<String, Boolean> permissions;

    public boolean isGranted(PermissionKey key) {
        return Boolean.TRUE.equals(permissions.get(key.jsonKey()));
    }

    public void setGranted(PermissionKey key, boolean granted) {
        permissions.put(key.jsonKey(), granted);
    }
}
