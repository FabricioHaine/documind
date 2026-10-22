package com.documind.rag.client;

import java.util.List;

/**
 * Contrato de provedor de embeddings — desacopla a aplicacao do servico externo.
 * Implementacoes DEVEM garantir que o modelo corrente corresponde ao
 * DOCUMIND_AI_EMBEDDING_MODEL configurado (nao misturar espacos de vetores).
 */
public interface EmbeddingProvider {

    boolean embeddingsDisponiveis();

    String modelo();

    int dimensaoConfigurada();

    /** Gera embeddings em lote, respeitando a ordem das entradas. */
    List<float[]> embedBatch(List<String> textos);
}
