package com.documind.common;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(403, "ACESSO_NEGADO", message);
    }
}
