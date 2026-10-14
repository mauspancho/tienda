package com.tienda.pos.whatsapp;

import com.tienda.pos.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_provider")
public class WhatsappProvider extends BaseEntity {
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(nullable = false, length = 500)
    private String baseUrl;
    @Column(nullable = false, length = 2048)
    private String encryptedSecret;
    private boolean enabled = true;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProviderStatus status = ProviderStatus.NOT_CHECKED;
    private LocalDateTime lastCheckedAt;
    @Column(length = 500)
    private String lastError;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getEncryptedSecret() { return encryptedSecret; }
    public void setEncryptedSecret(String encryptedSecret) { this.encryptedSecret = encryptedSecret; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public ProviderStatus getStatus() { return status; }
    public void setStatus(ProviderStatus status) { this.status = status; }
    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime value) { this.lastCheckedAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
