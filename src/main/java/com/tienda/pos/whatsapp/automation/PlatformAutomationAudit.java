package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "platform_automation_audit")
public class PlatformAutomationAudit extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id", nullable = false)
    private PlatformAutomationToken token;
    @Column(nullable = false, length = 120)
    private String action;
    @Column(length = 60)
    private String tenantCode;
    @Column(nullable = false)
    private int httpStatus;
    @Column(length = 80)
    private String remoteAddress;

    public void setToken(PlatformAutomationToken value) { this.token = value; }
    public void setAction(String value) { this.action = value; }
    public void setTenantCode(String value) { this.tenantCode = value; }
    public void setHttpStatus(int value) { this.httpStatus = value; }
    public void setRemoteAddress(String value) { this.remoteAddress = value; }
}
