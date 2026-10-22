package com.documind.rag.client;

import java.util.List;

/** Contrato de geracao de respostas (LLM de chat). */
public interface ChatProvider {

    boolean chatDisponivel();

    String modelo();

    ChatResult gerar(List<ChatMessage> mensagens);
}
