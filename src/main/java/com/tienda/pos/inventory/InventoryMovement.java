package com.tienda.pos.inventory;

import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.product.Product;
import com.tienda.pos.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_movement")
public class InventoryMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryMovementType movementType;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal previousStock;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal newStock;

    private String referenceType;
    private Long referenceId;
    private String notes;

    @Column(precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Column(precision = 14, scale = 2)
    private BigDecimal previousPurchaseCost;

    @Column(precision = 14, scale = 2)
    private BigDecimal newPurchaseCost;

    @Column(precision = 14, scale = 2)
    private BigDecimal costAdjustment;

    @Column(nullable = false)
    private boolean reversed = false;

    private LocalDateTime reversedAt;
    private String reversedBy;
    private Long reversalMovementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    public boolean isReversible() {
        if (reversed || isReversalMovement()) {
            return false;
        }
        return switch (movementType) {
            case PURCHASE, PURCHASE_RETURN, ADJUSTMENT_IN, ADJUSTMENT_OUT, INITIAL_STOCK -> true;
            case SALE, SALE_RETURN -> false;
        };
    }

    public boolean isReversalMovement() {
        return "REVERSAL".equals(referenceType);
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public InventoryMovementType getMovementType() { return movementType; }
    public void setMovementType(InventoryMovementType movementType) { this.movementType = movementType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPreviousStock() { return previousStock; }
    public void setPreviousStock(BigDecimal previousStock) { this.previousStock = previousStock; }
    public BigDecimal getNewStock() { return newStock; }
    public void setNewStock(BigDecimal newStock) { this.newStock = newStock; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public BigDecimal getPreviousPurchaseCost() { return previousPurchaseCost; }
    public void setPreviousPurchaseCost(BigDecimal previousPurchaseCost) { this.previousPurchaseCost = previousPurchaseCost; }
    public BigDecimal getNewPurchaseCost() { return newPurchaseCost; }
    public void setNewPurchaseCost(BigDecimal newPurchaseCost) { this.newPurchaseCost = newPurchaseCost; }
    public BigDecimal getCostAdjustment() { return costAdjustment; }
    public void setCostAdjustment(BigDecimal costAdjustment) { this.costAdjustment = costAdjustment; }
    public boolean isReversed() { return reversed; }
    public void setReversed(boolean reversed) { this.reversed = reversed; }
    public LocalDateTime getReversedAt() { return reversedAt; }
    public void setReversedAt(LocalDateTime reversedAt) { this.reversedAt = reversedAt; }
    public String getReversedBy() { return reversedBy; }
    public void setReversedBy(String reversedBy) { this.reversedBy = reversedBy; }
    public Long getReversalMovementId() { return reversalMovementId; }
    public void setReversalMovementId(Long reversalMovementId) { this.reversalMovementId = reversalMovementId; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
}
