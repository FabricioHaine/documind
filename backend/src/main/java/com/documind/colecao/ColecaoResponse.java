package com.documind.colecao;

import java.time.Instant;

public record ColecaoResponse(
        Long id,
        String nome,
        String descricao,
        Instant dataCriacao,
        Instant dataAtualizacao,
        long quantDocumentos
) {
}
