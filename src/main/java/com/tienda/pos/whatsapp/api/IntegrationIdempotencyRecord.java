package com.tienda.pos.whatsapp.api;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.tenant.Tenant;
import com.tienda.pos.whatsapp.TenantIntegrationToken;
import jakarta.persistence.*;

@Entity
@Table(name = "integration_idempotency_record")
public class IntegrationIdempotencyRecord extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private Tenant tenant;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private TenantIntegrationToken token;
    @Column(nullable = false, length = 80) private String operationName;
    @Column(nullable = false, length = 64) private String keyHash;
    @Column(nullable = false, length = 64) private String requestHash;
    @Lob @Column(nullable = false, columnDefinition = "LONGTEXT") private String responseJson;
    public Tenant getTenant() { return tenant; } public void setTenant(Tenant value) { tenant = value; }
    public TenantIntegrationToken getToken() { return token; } public void setToken(TenantIntegrationToken value) { token = value; }
    public String getOperationName() { return operationName; } public void setOperationName(String value) { operationName = value; }
    public String getKeyHash() { return keyHash; } public void setKeyHash(String value) { keyHash = value; }
    public String getRequestHash() { return requestHash; } public void setRequestHash(String value) { requestHash = value; }
    public String getResponseJson() { return responseJson; } public void setResponseJson(String value) { responseJson = value; }
}
