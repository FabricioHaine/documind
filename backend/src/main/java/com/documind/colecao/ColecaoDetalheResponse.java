package com.documind.colecao;

import java.time.Instant;
import java.util.List;

/** Detalhe da colecao com seus documentos. */
public record ColecaoDetalheResponse(
        Long id,
        String nome,
        String descricao,
        Instant dataCriacao,
        Instant dataAtualizacao,
        long quantDocumentos,
        List<DocumentoDaColecao> documentos
) {

    public record DocumentoDaColecao(
            Long id,
            String nomeOriginal,
            String tipoArquivo,
            long tamanhoBytes,
            Integer totalPaginas,
            String estado,
            Instant dataUpload
    ) {
    }
}
