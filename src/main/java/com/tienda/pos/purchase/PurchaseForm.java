package com.tienda.pos.purchase;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PurchaseForm {
    private Long supplierId;
    private String externalFolio;
    @NotNull
    private Long productId;
    @NotNull
    @DecimalMin("0.001")
    private BigDecimal quantity = BigDecimal.ONE;
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal unitCost = BigDecimal.ZERO;
    private boolean updateProductCost = true;
    @NotNull
    private PurchaseFundingSource fundingSource = PurchaseFundingSource.BUSINESS_CASH;
    private String notes;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getExternalFolio() { return externalFolio; }
    public void setExternalFolio(String externalFolio) { this.externalFolio = externalFolio; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    public boolean isUpdateProductCost() { return updateProductCost; }
    public void setUpdateProductCost(boolean updateProductCost) { this.updateProductCost = updateProductCost; }
    public PurchaseFundingSource getFundingSource() { return fundingSource; }
    public void setFundingSource(PurchaseFundingSource fundingSource) { this.fundingSource = fundingSource; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

