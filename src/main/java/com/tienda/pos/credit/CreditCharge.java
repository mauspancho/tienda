package com.tienda.pos.credit;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.customer.Customer;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_charge")
public class CreditCharge extends BaseEntity {
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "tenant_id", nullable = false)
    private com.tienda.pos.tenant.Tenant tenant;

    public com.tienda.pos.tenant.Tenant getTenant() { return tenant; }
    public void setTenant(com.tienda.pos.tenant.Tenant tenant) { this.tenant = tenant; }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private String concept;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditChargeStatus status = CreditChargeStatus.ACTIVE;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;
    private LocalDateTime reversedAt;
    private String reversedBy;
    private String reversalReason;
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getConcept() { return concept; }
    public void setConcept(String concept) { this.concept = concept; }
    public CreditChargeStatus getStatus() { return status; }
    public void setStatus(CreditChargeStatus status) { this.status = status; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public LocalDateTime getReversedAt() { return reversedAt; }
    public void setReversedAt(LocalDateTime reversedAt) { this.reversedAt = reversedAt; }
    public String getReversedBy() { return reversedBy; }
    public void setReversedBy(String reversedBy) { this.reversedBy = reversedBy; }
    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
}
