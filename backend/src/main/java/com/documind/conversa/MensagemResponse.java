package com.documind.conversa;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MensagemResponse(
        Long id,
        String autor,
        String estado,
        String conteudo,
        Instant dataCriacao,
        Map<String, Object> metadados,
        List<CitacaoResponse> citacoes
) {
}
