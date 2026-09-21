package com.example.protaxo.security.repository;

import com.example.protaxo.security.entity.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Atomically claims the token — the WHERE guard (not-yet-used, not-expired) and the write
     * happen as a single conditional UPDATE, so two concurrent requests for the same token can't
     * both pass an earlier isValid() check and both proceed: whichever commits first flips
     * used_at, and the row count tells the loser they were too late. See
     * PasswordResetService#resetPassword.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :usedAt "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
    int claim(@Param("id") Long id, @Param("usedAt") Instant usedAt, @Param("now") Instant now);
}
