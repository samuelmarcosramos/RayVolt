package com.rayvolt.backend.errors;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
