package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;

@Entity
@Table(name = "platform_automation_token")
public class PlatformAutomationToken extends BaseEntity {
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, unique = true, length = 24)
    private String tokenPrefix;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private String scopes;
    @Column(nullable = false)
    private boolean active = true;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime expiresAt;

    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String value) { this.tokenPrefix = value; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String value) { this.tokenHash = value; }
    public String getScopes() { return scopes; }
    public void setScopes(String value) { this.scopes = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime value) { this.lastUsedAt = value; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime value) { this.revokedAt = value; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime value) { this.expiresAt = value; }
    public boolean permits(AutomationScope scope) {
        return Arrays.stream(scopes.split(",")).map(String::trim).anyMatch(scope.name()::equals);
    }
}
