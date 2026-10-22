package com.documind.processamento;

/**
 * Estados da tarefa de processamento (fila persistida).
 */
public enum EstadoTarefa {
    PENDENTE,
    EM_EXECUCAO,
    CONCLUIDA,
    FALHA
}
