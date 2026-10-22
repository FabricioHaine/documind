package com.documind.common;

import org.springframework.http.HttpStatus;

/**
 * Erro relacionado a um arquivo enviado (formato, conteudo ou processamento).
 */
public class InvalidFileException extends ApiException {
    public InvalidFileException(String message) {
        super(HttpStatus.BAD_REQUEST.value(), "ARQUIVO_INVALIDO", message);
    }
}
