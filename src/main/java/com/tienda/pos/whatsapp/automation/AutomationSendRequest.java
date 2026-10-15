package com.tienda.pos.whatsapp.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutomationSendRequest(@NotBlank String groupId, @NotBlank @Size(max = 10000) String message) { }
