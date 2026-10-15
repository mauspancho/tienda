package com.tienda.pos.whatsapp;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.tenant.Tenant;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_whatsapp_connection")
public class TenantWhatsappConnection extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Tenant tenant;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private WhatsappProvider provider;
    @Column(nullable = false, length = 100)
    private String sessionName;
    @Column(nullable = false, length = 4096)
    private String encryptedSessionToken;
    @Column(length = 40)
    private String phoneNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WhatsappSessionStatus status = WhatsappSessionStatus.NOT_CONFIGURED;
    private LocalDateTime lastConnectedAt;
    private LocalDateTime lastCheckedAt;
    @Column(length = 500)
    private String lastError;

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public WhatsappProvider getProvider() { return provider; }
    public void setProvider(WhatsappProvider provider) { this.provider = provider; }
    public String getSessionName() { return sessionName; }
    public void setSessionName(String sessionName) { this.sessionName = sessionName; }
    public String getEncryptedSessionToken() { return encryptedSessionToken; }
    public void setEncryptedSessionToken(String value) { this.encryptedSessionToken = value; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public WhatsappSessionStatus getStatus() { return status; }
    public void setStatus(WhatsappSessionStatus status) { this.status = status; }
    public LocalDateTime getLastConnectedAt() { return lastConnectedAt; }
    public void setLastConnectedAt(LocalDateTime value) { this.lastConnectedAt = value; }
    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime value) { this.lastCheckedAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
