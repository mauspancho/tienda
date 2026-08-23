package com.tienda.pos.setup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetupForm {

    @NotBlank
    private String engine = "MariaDB";

    @NotBlank
    private String host = "localhost";

    @Min(1)
    private int port = 3306;

    @NotBlank
    private String databaseName = "tienda";

    @NotBlank
    private String databaseUser;

    private String databasePassword;
    private String additionalParams = "useUnicode=true&characterEncoding=utf8&serverTimezone=America/Mexico_City";

    @NotBlank
    private String adminFirstName;

    @NotBlank
    private String adminLastName;

    @NotBlank
    @Size(min = 4, max = 60)
    private String adminUsername;

    @Size(min = 8, max = 128)
    private String adminPassword;

    private String adminPasswordConfirm;

    @Email
    private String adminEmail;

    @NotBlank
    private String storeName = "Mi tienda";

    private String storePhone;
    private String storeAddress;
    private String taxId;
    private String currency = "MXN";
    private String currencySymbol = "$";
    private String timezone = "America/Mexico_City";

    public String jdbcUrl() {
        String driverPrefix = "MySQL".equalsIgnoreCase(engine) ? "mysql" : "mariadb";
        String params = additionalParams == null || additionalParams.isBlank() ? "" : "?" + additionalParams.trim();
        return "jdbc:%s://%s:%d/%s%s".formatted(driverPrefix, host.trim(), port, databaseName.trim(), params);
    }

    public boolean passwordsMatch() {
        return adminPassword != null && adminPassword.equals(adminPasswordConfirm);
    }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getDatabaseUser() { return databaseUser; }
    public void setDatabaseUser(String databaseUser) { this.databaseUser = databaseUser; }
    public String getDatabasePassword() { return databasePassword; }
    public void setDatabasePassword(String databasePassword) { this.databasePassword = databasePassword; }
    public String getAdditionalParams() { return additionalParams; }
    public void setAdditionalParams(String additionalParams) { this.additionalParams = additionalParams; }
    public String getAdminFirstName() { return adminFirstName; }
    public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }
    public String getAdminLastName() { return adminLastName; }
    public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public String getAdminPasswordConfirm() { return adminPasswordConfirm; }
    public void setAdminPasswordConfirm(String adminPasswordConfirm) { this.adminPasswordConfirm = adminPasswordConfirm; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getStorePhone() { return storePhone; }
    public void setStorePhone(String storePhone) { this.storePhone = storePhone; }
    public String getStoreAddress() { return storeAddress; }
    public void setStoreAddress(String storeAddress) { this.storeAddress = storeAddress; }
    public String getTaxId() { return taxId; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
}
