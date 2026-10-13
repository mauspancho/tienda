package com.tienda.pos.tenant;

import com.tienda.pos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    private boolean active = true;

    @Column(nullable = false)
    private long authEpoch;
    public long getAuthEpoch() { return authEpoch; }
    public void revokeApiTokens() { authEpoch++; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { if (this.active != active) authEpoch++; this.active = active; }
}
