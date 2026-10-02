package com.tienda.pos.api.v1.auth;

import com.tienda.pos.api.v1.config.ApiSecurityProperties;
import com.tienda.pos.user.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ApiTokenService {

    private final JwtEncoder jwtEncoder;
    private final ApiSecurityProperties properties;

    public ApiTokenService(JwtEncoder jwtEncoder, ApiSecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public String createAccessToken(AppUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("uid", user.getId())
                .claim("displayName", user.fullName())
                .claim("roles", user.getRoles().stream().map(role -> role.getName()).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }
}
