package com.tienda.pos.cash;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_register_session")
public class CashRegisterSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private AppUser cashier;

    @Column(nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    private LocalDateTime closedAt;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal expectedAmount;

    @Column(precision = 14, scale = 2)
    private BigDecimal countedAmount;

    @Column(precision = 14, scale = 2)
    private BigDecimal differenceAmount;

    private boolean open = true;

    public AppUser getCashier() { return cashier; }
    public void setCashier(AppUser cashier) { this.cashier = cashier; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getOpeningAmount() { return openingAmount; }
    public void setOpeningAmount(BigDecimal openingAmount) { this.openingAmount = openingAmount; }
    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
    public BigDecimal getCountedAmount() { return countedAmount; }
    public void setCountedAmount(BigDecimal countedAmount) { this.countedAmount = countedAmount; }
    public BigDecimal getDifferenceAmount() { return differenceAmount; }
    public void setDifferenceAmount(BigDecimal differenceAmount) { this.differenceAmount = differenceAmount; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
}
