package com.tienda.pos.platform;

import jakarta.validation.constraints.*;
import java.time.ZoneId;

public class TenantForm {
    @NotBlank @Size(max = 160)
    private String name;

    @NotBlank @Size(max = 160)
    private String businessName;

    @Size(max = 200)
    private String legalName;

    @Size(max = 80)
    private String taxId;

    @NotBlank @Size(max = 255)
    private String phone;

    @Email @Size(max = 255)
    private String email;

    @NotBlank @Size(max = 255)
    private String address;

    @NotBlank @Pattern(regexp = "[A-Z]{3}")
    private String currency = "MXN";

    @NotBlank @Size(max = 8)
    private String currencySymbol = "$";

    @NotBlank @Size(max = 255)
    private String timezone = "America/Mexico_City";

    @AssertTrue(message = "Zona horaria no valida.")
    public boolean isValidTimezone() {
        try { ZoneId.of(timezone); return true; }
        catch (RuntimeException exception) { return false; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
