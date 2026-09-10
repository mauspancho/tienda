package com.tienda.pos.platform;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class TenantCreateForm extends TenantForm {
    @NotBlank @Size(max = 60) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "Usa minusculas, numeros y guiones en el codigo.")
    private String code;

    @NotBlank @Size(max = 255)
    private String adminFirstName;

    @NotBlank @Size(max = 255)
    private String adminLastName;

    @NotBlank @Size(max = 60) @Pattern(regexp = "[a-zA-Z0-9._-]+", message = "El usuario solo admite letras, numeros, punto, guion y guion bajo.")
    private String adminUsername;

    @Email @Size(max = 255)
    private String adminEmail;

    @NotBlank @Size(min = 8, max = 72)
    private String password;

    @NotBlank @Size(min = 8, max = 72)
    private String confirmPassword;

    @AssertTrue(message = "Las contrasenas deben coincidir y no superar 72 bytes.")
    public boolean isPasswordConfirmed() {
        return password != null && Objects.equals(password, confirmPassword)
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public void clearPasswords() { password = null; confirmPassword = null; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getAdminFirstName() { return adminFirstName; }
    public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }
    public String getAdminLastName() { return adminLastName; }
    public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
