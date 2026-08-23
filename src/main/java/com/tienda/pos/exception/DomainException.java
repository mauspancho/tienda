package com.tienda.pos.exception;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
