package com.example.protaxo.security.repository;

import com.example.protaxo.security.entity.InvitationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationTokenRepository extends JpaRepository<InvitationToken, Long> {

    Optional<InvitationToken> findByToken(String token);

    /**
     * Atomically claims the token — see PasswordResetTokenRepository#claim for why this needs to
     * be one conditional UPDATE rather than a separate isValid() check plus save().
     */
    @Modifying
    @Query("UPDATE InvitationToken t SET t.usedAt = :usedAt "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
    int claim(@Param("id") Long id, @Param("usedAt") Instant usedAt, @Param("now") Instant now);
}
