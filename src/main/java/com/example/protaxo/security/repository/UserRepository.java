package com.example.protaxo.security.repository;

import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRoleAndActiveTrue(Role role);

    /** Bypasses the deleted_at @SQLRestriction, see VehicleRepository#findRegistrationNumberByIdIncludingDeleted. */
    @Query(value = "SELECT full_name FROM users WHERE id = :id", nativeQuery = true)
    Optional<String> findFullNameByIdIncludingDeleted(@Param("id") Long id);

    /**
     * Bulk update instead of load-modify-save — a login shouldn't bump the entity's
     * {@code @Version}/{@code updatedAt} (via {@code AuditingEntityListener}), which would make
     * every login look like a data edit in the audit trail's "updated" timestamp.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginAt WHERE u.email = :email")
    void updateLastLoginAt(@Param("email") String email, @Param("loginAt") Instant loginAt);
}
