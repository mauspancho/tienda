package com.tienda.pos.platform;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PasswordResetForm {
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(min = 8, max = 72)
    private String confirmPassword;

    @AssertTrue(message = "Las contrasenas deben coincidir y no superar 72 bytes.")
    public boolean isPasswordConfirmed() {
        return password != null && Objects.equals(password, confirmPassword)
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
