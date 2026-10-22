package com.documind.common;

import java.util.List;

/**
 * Corpo padrao de erro da API. Nunca inclui stack trace nem detalhes de infra.
 */
public record ApiError(int status, String code, String message, List<FieldViolation> violations) {

    public record FieldViolation(String campo, String mensagem) {
    }

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, List.of());
    }
}
