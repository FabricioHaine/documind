package com.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades da integracao de IA (prefixo `ai`).
 *
 * Padrao: qualquer servico compativel com a API OpenAI (Ollama, vLLM,
 * OpenRouter, Azure OpenAI com proxy, etc.). Nenhum modelo fica fixo no codigo:
 * o modelo escolhido vem sempre das variaveis de ambiente, centralizado aqui.
 *
 * Quando base-url/chat-model/embedding-model nao estao definidos, o pipeline
 * de RAG e considerado DESABILITADO (AiProperties#isConfigured() == false) e os
 * endpoints de IA respondem 503 com mensagem clara — o sistema nunca simula
 * respostas de RAG sem integracao real configurada.
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        int maxContextChunks,
        double minRelevanceThreshold,
        int requestTimeoutSeconds,
        int maxRetries,
        int embeddingBatchSize,
        int maxOutputTokens
) {

    public boolean isConfigured() {
        return isNotBlank(baseUrl) && isNotBlank(chatModel) && isNotBlank(embeddingModel);
    }

    public boolean embeddingsConfigured() {
        return isNotBlank(baseUrl) && isNotBlank(embeddingModel);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
