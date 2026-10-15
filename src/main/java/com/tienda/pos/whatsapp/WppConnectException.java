package com.tienda.pos.whatsapp;

public class WppConnectException extends RuntimeException {
    public enum Kind { INVALID_CREDENTIALS, UNREACHABLE, TIMEOUT, INCOMPATIBLE, REMOTE_ERROR }
    private final Kind kind;
    public WppConnectException(Kind kind, String message) { super(message); this.kind = kind; }
    public WppConnectException(Kind kind, String message, Throwable cause) { super(message, cause); this.kind = kind; }
    public Kind getKind() { return kind; }
}
