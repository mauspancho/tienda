package com.tienda.pos.credit;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.customer.Customer;
import com.tienda.pos.payment.PaymentMethod;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "credit_payment")
public class CreditPayment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    private String notes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditPaymentStatus status = CreditPaymentStatus.ACTIVE;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;
    private LocalDateTime reversedAt;
    private String reversedBy;
    private String reversalReason;
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CreditPaymentApplication> applications = new ArrayList<>();

    public void addApplication(CreditPaymentApplication application) { applications.add(application); application.setPayment(this); }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public CreditPaymentStatus getStatus() { return status; }
    public void setStatus(CreditPaymentStatus status) { this.status = status; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public LocalDateTime getReversedAt() { return reversedAt; }
    public void setReversedAt(LocalDateTime reversedAt) { this.reversedAt = reversedAt; }
    public String getReversedBy() { return reversedBy; }
    public void setReversedBy(String reversedBy) { this.reversedBy = reversedBy; }
    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
    public List<CreditPaymentApplication> getApplications() { return applications; }
}
