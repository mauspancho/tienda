package com.tienda.pos.externalproduct;

public class ExternalProductLookupException extends RuntimeException {
    public ExternalProductLookupException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalProductLookupException(String message) {
        super(message);
    }
}
