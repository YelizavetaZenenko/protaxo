package com.example.protaxo.security.repository;

import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.entity.RolePermissions;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionsRepository extends JpaRepository<RolePermissions, Long> {
    Optional<RolePermissions> findByRole(Role role);
}
