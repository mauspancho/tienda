package com.tienda.pos.whatsapp.automation;

public class AutomationRateLimitException extends RuntimeException {
    public AutomationRateLimitException(String message) {
        super(message);
    }
}
