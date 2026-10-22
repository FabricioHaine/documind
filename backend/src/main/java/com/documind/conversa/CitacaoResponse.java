package com.documind.conversa;

/**
 * Fonte verificavel de uma resposta. Clicar abre o detalhe do documento
 * (documindo id) e mostra o trecho correspondente (pagina/indice).
 */
public record CitacaoResponse(
        Long id,
        Long documentoId,
        String documentoNome,
        Long trechoId,
        int referencia,
        int indiceTrecho,
        Integer paginaInicio,
        Integer paginaFim,
        String conteudoTrecho
) {
}
