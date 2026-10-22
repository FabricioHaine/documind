package com.documind.documento;

/**
 * Estados de processamento de um documento (espelha a migracao V1).
 */
public enum EstadoProcessamento {
    ENVIADO,
    AGUARDANDO_PROCESSAMENTO,
    PROCESSANDO,
    CONCLUIDO,
    FALHOU;

    /** Estados de entrega de conteudo para o RAG. */
    public boolean prontoParaUso() {
        return this == CONCLUIDO;
    }
}
