package com.tienda.pos.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class InventoryAdjustmentForm {
    @NotNull
    private Long productId;
    @NotNull
    private InventoryMovementType movementType = InventoryMovementType.ADJUSTMENT_IN;
    @NotNull
    @DecimalMin("0.001")
    private BigDecimal quantity = BigDecimal.ONE;
    @DecimalMin("0.00")
    private BigDecimal unitCost;
    private String notes;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public InventoryMovementType getMovementType() { return movementType; }
    public void setMovementType(InventoryMovementType movementType) { this.movementType = movementType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

