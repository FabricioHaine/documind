package com.documind.common;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(409, "CONFLITO", message);
    }
}
