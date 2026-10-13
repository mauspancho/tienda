package com.tienda.pos.credit;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.sale.Sale;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "credit_payment_application")
public class CreditPaymentApplication extends BaseEntity {
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "tenant_id", nullable = false)
    private com.tienda.pos.tenant.Tenant tenant;

    public com.tienda.pos.tenant.Tenant getTenant() { return tenant; }
    public void setTenant(com.tienda.pos.tenant.Tenant tenant) { this.tenant = tenant; }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_payment_id")
    private CreditPayment payment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_charge_id")
    private CreditCharge charge;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    public CreditPayment getPayment() { return payment; }
    public void setPayment(CreditPayment payment) { this.payment = payment; }
    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }
    public CreditCharge getCharge() { return charge; }
    public void setCharge(CreditCharge charge) { this.charge = charge; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
