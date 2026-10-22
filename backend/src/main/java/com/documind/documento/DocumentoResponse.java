package com.documind.documento;

import java.time.Instant;
import java.util.List;

/** Representacao de documento para listagens (sem conteudo pesado). */
public record DocumentoResponse(
        Long id,
        String nomeOriginal,
        String tipoArquivo,
        String tipoMime,
        long tamanhoBytes,
        Integer totalPaginas,
        String estado,
        String mensagemErro,
        Instant dataUpload,
        Instant dataProcessamento,
        List<ColecaoResumo> colecoes
) {

    public record ColecaoResumo(Long id, String nome) {
    }
}
