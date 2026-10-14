package com.tienda.pos.whatsapp;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.tenant.Tenant;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_group")
public class WhatsappGroup extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Tenant tenant;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private TenantWhatsappConnection connection;
    @Column(nullable = false, length = 180)
    private String whatsappId;
    @Column(nullable = false)
    private String name;
    private Integer participantCount;
    private boolean available = true;
    @Column(nullable = false)
    private LocalDateTime refreshedAt;

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public TenantWhatsappConnection getConnection() { return connection; }
    public void setConnection(TenantWhatsappConnection connection) { this.connection = connection; }
    public String getWhatsappId() { return whatsappId; }
    public void setWhatsappId(String whatsappId) { this.whatsappId = whatsappId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getParticipantCount() { return participantCount; }
    public void setParticipantCount(Integer participantCount) { this.participantCount = participantCount; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public LocalDateTime getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(LocalDateTime refreshedAt) { this.refreshedAt = refreshedAt; }
}
