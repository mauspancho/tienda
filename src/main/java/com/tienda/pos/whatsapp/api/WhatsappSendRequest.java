package com.tienda.pos.whatsapp.api;

import jakarta.validation.constraints.NotBlank;

public record WhatsappSendRequest(@NotBlank String phone, boolean isGroup, @NotBlank String message) { }
