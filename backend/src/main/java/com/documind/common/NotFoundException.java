package com.documind.common;

public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(404, "NAO_ENCONTRADO", message);
    }
}
