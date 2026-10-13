package com.tienda.pos.api.v1.error;

public class ApiNotFoundException extends RuntimeException {
    public ApiNotFoundException(String message) {
        super(message);
    }
}
