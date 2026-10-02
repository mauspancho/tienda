package com.tienda.pos.api.v1.auth;

import com.tienda.pos.api.v1.config.ApiSecurityProperties;
import com.tienda.pos.api.v1.error.ApiAuthenticationException;
import com.tienda.pos.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class ApiRefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiRefreshTokenRepository repository;
    private final ApiSecurityProperties properties;

    public ApiRefreshTokenService(ApiRefreshTokenRepository repository, ApiSecurityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public IssuedRefreshToken issue(AppUser user) {
        String rawToken = randomToken();
        ApiRefreshToken token = new ApiRefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plus(properties.getRefreshTokenTtl()));
        repository.save(token);
        return new IssuedRefreshToken(rawToken, user);
    }

    @Transactional
    public IssuedRefreshToken rotate(String rawToken) {
        String tokenHash = hash(rawToken);
        ApiRefreshToken current = repository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ApiAuthenticationException("Refresh token inválido."));
        LocalDateTime now = LocalDateTime.now();
        if (current.getRevokedAt() != null) {
            repository.revokeAllActiveByUser(current.getUser().getId(), now);
            throw new ApiAuthenticationException("Refresh token revocado.");
        }
        if (!current.getExpiresAt().isAfter(now) || !current.getUser().isActive()) {
            current.setRevokedAt(now);
            throw new ApiAuthenticationException("Refresh token expirado.");
        }
        IssuedRefreshToken replacement = issue(current.getUser());
        current.setRevokedAt(now);
        current.setReplacedByHash(hash(replacement.rawToken()));
        repository.save(current);
        return replacement;
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                repository.save(token);
            }
        });
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiAuthenticationException("Refresh token requerido.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    public record IssuedRefreshToken(String rawToken, AppUser user) {
    }
}
