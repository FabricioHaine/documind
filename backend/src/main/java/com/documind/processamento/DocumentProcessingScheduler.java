package com.documind.processamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recovery e reenfileiramento das tarefas de processamento.
 *
 * Limitacao documentada (MVP): a fila e o executor vivem em memoria. Se a
 * aplicacao morrer no meio do processamento:
 *  - tarefas PENDENTE sao reenfileiradas pelo loop periodico;
 *  - tarefas EM_EXECUCAO orfas (crash) sao resetadas para PENDENTE no startup.
 */
@Component
public class DocumentProcessingScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingScheduler.class);
    private static final int LOTE_POR_CICLO = 10;

    private final TarefaProcessamentoRepository tarefaRepository;
    private final DocumentProcessingService processamento;

    public DocumentProcessingScheduler(TarefaProcessamentoRepository tarefaRepository,
                                       DocumentProcessingService processamento) {
        this.tarefaRepository = tarefaRepository;
        this.processamento = processamento;
    }

    /** No startup: nenhuma tarefa esta realmente executando — reset EM_EXECUCAO. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recuperarTarefasOrfas() {
        List<TarefaProcessamento> orfas = tarefaRepository.findByEstado(EstadoTarefa.EM_EXECUCAO);
        for (TarefaProcessamento tarefa : orfas) {
            tarefa.reenfileirar();
            tarefaRepository.save(tarefa);
        }
        if (!orfas.isEmpty()) {
            LOG.info("Recuperadas {} tarefas de processamento orfas apos reinicializacao", orfas.size());
        }
    }

    /** Loop periodico: entrega tarefas pendentes (principalmente pos-restart). */
    @Scheduled(fixedDelay = 60_000)
    public void enfileirarPendentes() {
        List<TarefaProcessamento> pendentes = tarefaRepository.findByEstado(EstadoTarefa.PENDENTE);
        int entregues = 0;
        for (TarefaProcessamento tarefa : pendentes) {
            if (entregues >= LOTE_POR_CICLO) {
                break;
            }
            processamento.enfileirar(tarefa.getDocumentoId());
            entregues++;
        }
    }
}
