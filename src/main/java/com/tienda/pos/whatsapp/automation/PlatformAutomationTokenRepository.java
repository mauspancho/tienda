package com.tienda.pos.whatsapp.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlatformAutomationTokenRepository extends JpaRepository<PlatformAutomationToken, Long> {
    @Transactional(readOnly = true)
    Optional<PlatformAutomationToken> findByTokenHashAndActiveTrue(String tokenHash);
    List<PlatformAutomationToken> findAllByOrderByCreatedAtDesc();

    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query("""
            update PlatformAutomationToken token
               set token.lastUsedAt = :usedAt
             where token.id = :tokenId
               and token.active = true
               and token.revokedAt is null
               and (token.lastUsedAt is null or token.lastUsedAt < :cutoff)
            """)
    int touchLastUsedAt(@Param("tokenId") Long tokenId,
                        @Param("usedAt") LocalDateTime usedAt,
                        @Param("cutoff") LocalDateTime cutoff);
}
