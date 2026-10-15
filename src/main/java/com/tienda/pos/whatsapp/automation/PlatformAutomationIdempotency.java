package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.tenant.Tenant;
import jakarta.persistence.*;

@Entity
@Table(name = "platform_automation_idempotency")
public class PlatformAutomationIdempotency extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private PlatformAutomationToken token;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Tenant tenant;
    @Column(nullable = false, length = 80)
    private String operationName;
    @Column(nullable = false, length = 64)
    private String keyHash;
    @Column(nullable = false, length = 64)
    private String requestHash;
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String responseJson;

    public void setToken(PlatformAutomationToken value) { this.token = value; }
    public void setTenant(Tenant value) { this.tenant = value; }
    public void setOperationName(String value) { this.operationName = value; }
    public void setKeyHash(String value) { this.keyHash = value; }
    public void setRequestHash(String value) { this.requestHash = value; }
    public void setResponseJson(String value) { this.responseJson = value; }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
}
