package com.documind.rag.client;

import java.util.List;

/** Resultado de uma geracao de resposta (com uso de tokens quando disponivel). */
public record ChatResult(String content, Integer promptTokens, Integer completionTokens) {

    public static ChatResult semUso(String content) {
        return new ChatResult(content, null, null);
    }

    public boolean contentIsEmpty() {
        return content == null || content.isBlank();
    }
}
