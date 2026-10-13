package com.tienda.pos.api.v1.error;

public class ApiConflictException extends RuntimeException {
    public ApiConflictException(String message) {
        super(message);
    }
}
