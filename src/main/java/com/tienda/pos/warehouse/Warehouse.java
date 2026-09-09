package com.tienda.pos.warehouse;

import com.tienda.pos.branch.Branch;
import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "warehouse", uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouse_tenant_branch_code", columnNames = {"tenant_id", "branch_id", "code"})
})
public class Warehouse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    private boolean mainWarehouse = true;
    private boolean active = true;

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isMainWarehouse() { return mainWarehouse; }
    public void setMainWarehouse(boolean mainWarehouse) { this.mainWarehouse = mainWarehouse; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}