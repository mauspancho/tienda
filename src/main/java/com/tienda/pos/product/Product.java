package com.tienda.pos.product;

import com.tienda.pos.category.Category;
import com.tienda.pos.common.BaseEntity;
import com.tienda.pos.common.MoneyUtils;
import com.tienda.pos.supplier.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @NotBlank
    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(unique = true, length = 80)
    private String barcode;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(length = 160)
    private String brand;

    @Column(length = 120)
    private String presentation;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal purchaseCost = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal salePrice = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.000")
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.000")
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitType unit = UnitType.PIEZA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(precision = 8, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    private boolean active = true;

    @Column(nullable = false)
    private boolean promoted = false;

    @Column(name = "promotion_order")
    private Integer promotionOrder;

    public BigDecimal unitProfit() {
        return MoneyUtils.money(salePrice).subtract(MoneyUtils.money(purchaseCost));
    }

    public BigDecimal marginPercent() {
        return MoneyUtils.marginPercent(salePrice, purchaseCost);
    }

    public boolean hasLowStock() {
        return currentStock != null && minimumStock != null && currentStock.compareTo(minimumStock) <= 0;
    }

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
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
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
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isPromoted() { return promoted; }
    public void setPromoted(boolean promoted) { this.promoted = promoted; }
    public Integer getPromotionOrder() { return promotionOrder; }
    public void setPromotionOrder(Integer promotionOrder) { this.promotionOrder = promotionOrder; }
}
