package com.documind.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginacao estavel para a API (independente do formato interno do Spring).
 */
public record PageResponse<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
