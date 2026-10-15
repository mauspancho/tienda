package com.tienda.pos.whatsapp;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.tenant.Tenant;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_whatsapp_announcement_settings")
public class TenantWhatsappAnnouncementSettings extends BaseEntity {
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;
    @Column(nullable = false)
    private boolean enabled;
    private Long confirmedConnectionId;
    private Long confirmedProviderId;
    @Column(length = 40)
    private String confirmedPhoneNumber;
    private LocalDateTime confirmedAt;

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getConfirmedConnectionId() { return confirmedConnectionId; }
    public void setConfirmedConnectionId(Long value) { this.confirmedConnectionId = value; }
    public Long getConfirmedProviderId() { return confirmedProviderId; }
    public void setConfirmedProviderId(Long value) { this.confirmedProviderId = value; }
    public String getConfirmedPhoneNumber() { return confirmedPhoneNumber; }
    public void setConfirmedPhoneNumber(String value) { this.confirmedPhoneNumber = value; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime value) { this.confirmedAt = value; }
}
