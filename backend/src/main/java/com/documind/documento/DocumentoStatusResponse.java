package com.documind.documento;

import java.time.Instant;

/** Resposta compacta para polling de estado (evita recarregar o detalhe). */
public record DocumentoStatusResponse(
        Long id,
        String estado,
        String mensagemErro,
        long quantTrechos,
        Instant dataProcessamento
) {
}
