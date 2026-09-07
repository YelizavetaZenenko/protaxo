package com.example.protaxo.security.repository;

import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findByRoleAndActiveTrue(Role role);
}
