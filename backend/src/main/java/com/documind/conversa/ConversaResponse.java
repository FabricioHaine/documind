package com.documind.conversa;

import java.time.Instant;

public record ConversaResponse(
        Long id,
        String titulo,
        Long colecaoId,
        String colecaoNome,
        Instant dataCriacao,
        Instant dataAtualizacao
) {
}
