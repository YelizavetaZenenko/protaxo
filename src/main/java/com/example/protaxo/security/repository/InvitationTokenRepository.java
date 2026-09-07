package com.example.protaxo.security.repository;

import com.example.protaxo.security.entity.InvitationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {

    Optional<InvitationToken> findByToken(String token);
}
