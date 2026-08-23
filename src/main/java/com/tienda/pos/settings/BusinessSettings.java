package com.tienda.pos.settings;

import com.tienda.pos.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "business_settings")
public class BusinessSettings extends BaseEntity {

    @Column(nullable = false)
    private String storeName = "Mi tienda";
    private String address;
    private String phone;
    private String taxId;
    private String currency = "MXN";
    private String currencySymbol = "$";
    private String timezone = "America/Mexico_City";
    @Column(precision = 8, scale = 2)
    private BigDecimal defaultTax = BigDecimal.ZERO;
    private String logoPath;
    private boolean negativeStockAllowed = false;

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public BigDecimal getDefaultTax() { return defaultTax; }
    public void setDefaultTax(BigDecimal defaultTax) { this.defaultTax = defaultTax; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    public boolean isNegativeStockAllowed() { return negativeStockAllowed; }
    public void setNegativeStockAllowed(boolean negativeStockAllowed) { this.negativeStockAllowed = negativeStockAllowed; }
}
