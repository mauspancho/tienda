package com.tienda.pos.whatsapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WhatsappProviderForm {
    private Long id;
    @NotBlank @Size(max = 120)
    private String name;
    @NotBlank @Size(max = 500)
    private String baseUrl;
    @Size(max = 1000)
    private String secret;
    private boolean enabled = true;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { this.baseUrl = value; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
