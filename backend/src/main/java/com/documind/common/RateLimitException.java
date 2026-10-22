package com.documind.common;

public class RateLimitException extends ApiException {
    public RateLimitException(String message) {
        super(429, "LIMITE_EXCEDIDO", message);
    }
}
