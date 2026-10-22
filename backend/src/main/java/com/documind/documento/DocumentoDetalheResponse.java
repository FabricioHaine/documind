package com.documind.documento;

import java.time.Instant;
import java.util.List;

/** Dados completos do documento, incluindo metadados de processamento. */
public record DocumentoDetalheResponse(
        Long id,
        String nomeOriginal,
        String tipoArquivo,
        String tipoMime,
        long tamanhoBytes,
        Integer totalPaginas,
        String estado,
        String mensagemErro,
        String modeloEmbedding,
        Instant dataUpload,
        Instant dataProcessamento,
        long quantTrechos,
        String tarefaEstado,
        Integer tarefaTentativas,
        String tarefaUltimoErro,
        List<DocumentoResponse.ColecaoResumo> colecoes
) {
}
