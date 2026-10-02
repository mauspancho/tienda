package com.tienda.pos.api.v1.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ApiRefreshTokenRepository extends JpaRepository<ApiRefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ApiRefreshToken t join fetch t.user u join fetch u.roles where t.tokenHash = :hash")
    Optional<ApiRefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update ApiRefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllActiveByUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
