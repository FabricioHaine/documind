package com.documind.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Tratamento global de excecoes: respostas consistentes, sem stack trace e
 * sem exposicao de detalhes internos de infraestrutura. O status HTTP e
 * sempre aplicado na resposta (ResponseEntity.status), nunca apenas no corpo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> handleBindExceptions(BindException ex) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        return new ApiError.FieldViolation(fieldError.getField(), error.getDefaultMessage());
                    }
                    return new ApiError.FieldViolation(null, error.getDefaultMessage());
                })
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "VALIDACAO", "Requisicao invalida.", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDACAO", "Corpo da requisicao invalido ou ausente."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "VALIDACAO", "Parametro com formato invalido: " + ex.getName()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "ARQUIVO_GRANDE", "O arquivo excede o tamanho maximo permitido."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(ApiError.of(403, "ACESSO_NEGADO", "Voce nao tem permissao para este recurso."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(401)
                .body(ApiError.of(401, "NAO_AUTENTICADO", "Sessao nao autenticada ou expirada."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(404)
                .body(ApiError.of(404, "NAO_ENCONTRADO", "Recurso nao encontrado."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        LOG.error("Erro inesperado na API", ex);
        return ResponseEntity.status(500)
                .body(ApiError.of(500, "ERRO_INTERNO", "Erro interno inesperado. Tente novamente mais tarde."));
    }
}
