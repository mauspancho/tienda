package com.tienda.pos.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class ProductForm {
    private Long id;
    @NotBlank
    private String code;
    private String barcode;
    @NotBlank
    private String name;
    private String brand;
    private String presentation;
    private String imageUrl;
    private String description;
    private Long categoryId;
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal purchaseCost = BigDecimal.ZERO;
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal salePrice = BigDecimal.ZERO;
    @NotNull
    @DecimalMin("0.000")
    private BigDecimal currentStock = BigDecimal.ZERO;
    @NotNull
    @DecimalMin("0.000")
    private BigDecimal minimumStock = BigDecimal.ZERO;
    private UnitType unit = UnitType.PIEZA;
    private Long supplierId;
    private BigDecimal tax = BigDecimal.ZERO;
    private boolean active = true;
    private boolean removeImage;

    public static ProductForm from(Product product) {
        ProductForm form = new ProductForm();
        form.id = product.getId();
        form.code = product.getCode();
        form.barcode = product.getBarcode();
        form.name = product.getName();
        form.brand = product.getBrand();
        form.presentation = product.getPresentation();
        form.imageUrl = product.getImageUrl();
        form.description = product.getDescription();
        form.categoryId = product.getCategory() == null ? null : product.getCategory().getId();
        form.purchaseCost = product.getPurchaseCost();
        form.salePrice = product.getSalePrice();
        form.currentStock = product.getCurrentStock();
        form.minimumStock = product.getMinimumStock();
        form.unit = product.getUnit();
        form.supplierId = product.getSupplier() == null ? null : product.getSupplier().getId();
        form.tax = product.getTax();
        form.active = product.isActive();
        return form;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getPresentation() { return presentation; }
    public void setPresentation(String presentation) { this.presentation = presentation; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(BigDecimal purchaseCost) { this.purchaseCost = purchaseCost; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public BigDecimal getCurrentStock() { return currentStock; }
    public void setCurrentStock(BigDecimal currentStock) { this.currentStock = currentStock; }
    public BigDecimal getMinimumStock() { return minimumStock; }
    public void setMinimumStock(BigDecimal minimumStock) { this.minimumStock = minimumStock; }
    public UnitType getUnit() { return unit; }
    public void setUnit(UnitType unit) { this.unit = unit; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isRemoveImage() { return removeImage; }
    public void setRemoveImage(boolean removeImage) { this.removeImage = removeImage; }
}
