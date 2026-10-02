package com.tienda.pos.api.v1.error;

public class ApiAuthenticationException extends RuntimeException {
    public ApiAuthenticationException(String message) {
        super(message);
    }
}
