package com.documind.common;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(400, "REQUISICAO_INVALIDA", message);
    }
}
