package com.documind.common;

/**
 * A integracao de IA nao esta configurada (falta base-url/modelo no ambiente).
 * A resposta deve deixar claro que o RAG esta pendente de configuracao e nao
 * simular funcionamento.
 */
public class AiNotConfiguredException extends ApiException {
    public AiNotConfiguredException() {
        super(503, "IA_NAO_CONFIGURADA",
                "A integracao de IA nao esta configurada. Defina DOCUMIND_AI_BASE_URL, "
                + "DOCUMIND_AI_CHAT_MODEL e DOCUMIND_AI_EMBEDDING_MODEL no ambiente do back-end. "
                + "Sem isso o pipeline de RAG permanece desabilitado.");
    }
}
