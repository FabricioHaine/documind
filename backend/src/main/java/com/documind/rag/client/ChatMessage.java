package com.documind.rag.client;

/** Mensagem de chat no formato do contrato compativel com OpenAI. */
public record ChatMessage(String role, String content) {

    public static ChatMessage sistema(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage usuario(String content) {
        return new ChatMessage("user", content);
    }
}
