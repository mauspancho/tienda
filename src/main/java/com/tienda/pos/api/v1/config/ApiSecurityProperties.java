package com.tienda.pos.api.v1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "tienda.api")
public class ApiSecurityProperties {

    private String issuer = "tienda-pos";
    private String jwtSecret = "";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private List<String> corsAllowedOrigins = new ArrayList<>();

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    public List<String> getCorsAllowedOrigins() { return corsAllowedOrigins; }
    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins == null ? new ArrayList<>() : corsAllowedOrigins;
    }
}
