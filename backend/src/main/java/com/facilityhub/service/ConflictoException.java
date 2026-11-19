package com.facilityhub.service;

// FH-006: conflicto de estado o de concurrencia (HTTP 409).
public class ConflictoException extends RuntimeException {
    public ConflictoException(String message) {
        super(message);
    }
}
