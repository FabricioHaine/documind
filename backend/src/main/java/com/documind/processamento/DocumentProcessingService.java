package com.documind.processamento;

import com.documind.config.AiProperties;
import com.documind.common.AiNotConfiguredException;
import com.documind.common.AiProviderException;
import com.documind.common.InvalidFileException;
import com.documind.documento.ChunkingService;
import com.documind.documento.Documento;
import com.documind.documento.DocumentoRepository;
import com.documind.documento.EstadoProcessamento;
import com.documind.documento.TextExtractionService;
import com.documind.documento.TextoExtraido;
import com.documind.documento.storage.FileStorageService;
import com.documind.rag.Vetores;
import com.documind.rag.client.EmbeddingProvider;
import com.documind.rag.VectorSearchRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Pipeline de ingestao (assincrono):
 *   extracao -> normalizacao -> chunking -> embeddings em lote ->
 *   persistencia de trechos + vetores (pgvector) -> CONCLUIDO.
 *
 * Resiliencia:
 * - reprocesso idempotente (trechos apagados e reescritos por upsert);
 * - cancelamento cooperativo (exclusao durante processamento): cada lote
 *   revalida existencia do documento antes de continuar;
 * - falha transitoria do provedor: ate 3 tentativas com reenfileiramento;
 * - falha definitiva/entrada invalida: documento FALHOU com mensagem segura;
 * - executor em memoria: scheduler recupera tarefas pendentes ao iniciar.
 *
 * NAO registra conteudo integral de documentos nos logs (so identificadores).
 */
@Service
public class DocumentProcessingService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingService.class);
    private static final int TENTATIVAS_MAXIMAS = 3;
    private static final int ATRASO_RE_TENTATIVA_SEGUNDOS = 30;

    private final DocumentoRepository documentoRepository;
    private final TarefaProcessamentoRepository tarefaRepository;
    private final FileStorageService storage;
    private final TextExtractionService extracao;
    private final ChunkingService chunking;
    private final EmbeddingProvider embeddingProvider;
    private final VectorSearchRepository vectorSearchRepository;
    private final AiProperties aiProperties;
    private final Executor processamentoExecutor;

    /** Documentos cujo processamento foi cancelado (exclusao em andamento). */
    private final Set<Long> cancelamentosSolicitados = ConcurrentHashMap.newKeySet();
    /** Evita enfileirar o mesmo documento duas vezes simultaneamente. */
    private final Set<Long> emExecucao = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService reTentativas = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "doc-process-retry");
        thread.setDaemon(true);
        return thread;
    });

    public DocumentProcessingService(DocumentoRepository documentoRepository,
                                     TarefaProcessamentoRepository tarefaRepository,
                                     FileStorageService storage,
                                     TextExtractionService extracao,
                                     ChunkingService chunking,
                                     EmbeddingProvider embeddingProvider,
                                     VectorSearchRepository vectorSearchRepository,
                                     AiProperties aiProperties,
                                     @Qualifier(com.documind.config.AsyncConfig.DOCUMENT_PROCESSING_EXECUTOR)
                                     Executor processamentoExecutor) {
        this.documentoRepository = documentoRepository;
        this.tarefaRepository = tarefaRepository;
        this.storage = storage;
        this.extracao = extracao;
        this.chunking = chunking;
        this.embeddingProvider = embeddingProvider;
        this.vectorSearchRepository = vectorSearchRepository;
        this.aiProperties = aiProperties;
        this.processamentoExecutor = processamentoExecutor;
    }

    @PreDestroy
    void aoDesligar() {
        reTentativas.shutdownNow();
    }

    // ------------------------------------------------------------------ //
    // Enfileiramento                                                      //
    // ------------------------------------------------------------------ //

    /** Marca AGUARDANDO e entrega o trabalho ao executor (fora da transacao). */
    @Transactional
    public void enfileirar(Long documentoId) {
        if (emExecucao.contains(documentoId)) {
            return;
        }
        Documento documento = documentoRepository.findWithUsuarioById(documentoId).orElse(null);
        if (documento == null) {
            return; // excluido antes de começar algo
        }
        TarefaProcessamento tarefa = tarefaRepository
                .findFirstByDocumentoIdOrderByDataCriacaoDesc(documentoId)
                .orElseGet(() -> {
                    TarefaProcessamento novaTarefa = TarefaProcessamento.nova(documentoId);
                    return tarefaRepository.save(novaTarefa);
                });
        tarefa.reenfileirar();
        tarefaRepository.save(tarefa);

        documento.setEstado(EstadoProcessamento.AGUARDANDO_PROCESSAMENTO);
        documentoRepository.save(documento);

        emExecucao.add(documentoId);
        processamentoExecutor.execute(() -> {
            try {
                processar(documentoId);
            } finally {
                emExecucao.remove(documentoId);
            }
        });
    }

    // ------------------------------------------------------------------ //
    // Pipeline                                                            //
    // ------------------------------------------------------------------ //

    public void processar(Long documentoId) {
        Instant inicio = Instant.now();
        Optional<Documento> documentoOpt = documentoRepository.findWithUsuarioById(documentoId);
        if (documentoOpt.isEmpty()) {
            LOG.info("Processamento abortado: documento {} removido", documentoId);
            cancelamentosSolicitados.remove(documentoId);
            return;
        }
        Documento documento = documentoOpt.get();
        TarefaProcessamento tarefa = tarefaRepository
                .findFirstByDocumentoIdOrderByDataCriacaoDesc(documentoId)
                .orElseGet(() -> tarefaRepository.save(TarefaProcessamento.nova(documentoId)));

        try {
            tarefa.marcarEmExecucao();
            tarefaRepository.save(tarefa);
            documento.setEstado(EstadoProcessamento.PROCESSANDO);
            documentoRepository.save(documento);

            validarConfiguracaoIA();

            // 1) Extracao (com paginas quando o formato fornece)
            TextoExtraido texto = extracao.extrair(
                    storage.resolver(documento.getNomeArmazenamento()), documento.getTipoArquivo());

            // 2) Chunking
            List<ChunkingService.Pedaco> pedacos = chunking.dividir(texto);
            if (pedacos.isEmpty()) {
                throw new InvalidFileException("Nao foi possivel extrair trechos do documento.");
            }

            // 3) Reprocessamento idempotente: apaga trechos anteriores
            vectorSearchRepository.excluirTrechosDoDocumento(documentoId);

            // 4) Embeddings em lote + persistencia dos trechos e vetores
            String modelo = aiProperties.embeddingModel();
            int tamanhoLote = Math.max(1, aiProperties.embeddingBatchSize());
            int totalTrechos = pedacos.size();
            int concluidos = 0;
            for (List<ChunkingService.Pedaco> lote : lotes(pedacos, tamanhoLote)) {
                verificarCancelamento(documentoId);
                List<String> conteudos = lote.stream().map(ChunkingService.Pedaco::conteudo).toList();
                List<float[]> vetores = embeddingProvider.embedBatch(conteudos);
                for (int i = 0; i < lote.size(); i++) {
                    ChunkingService.Pedaco pedaco = lote.get(i);
                    vectorSearchRepository.inserir(
                            documentoId, pedaco.indice(), pedaco.conteudo(),
                            pedaco.paginaInicio(), pedaco.paginaFim(),
                            metadadosJson(pedaco.metadados()), modelo,
                            Vetores.paraLiteral(vetores.get(i)));
                }
                concluidos += lote.size();
            }

            verificarCancelamento(documentoId);

            // 5) Conclusao
            documento.setTotalPaginas(texto.totalPaginas());
            documento.setModeloEmbedding(modelo);
            documento.setEstado(EstadoProcessamento.CONCLUIDO);
            documento.setMensagemErro(null);
            documento.setDataProcessamento(Instant.now());
            documentoRepository.save(documento);

            tarefa.marcarConcluida();
            tarefaRepository.save(tarefa);

            LOG.info("Documento {} processado: {} trechos, paginas={}, {} ms",
                    documentoId, totalTrechos,
                    texto.totalPaginas() == null ? "n/a" : texto.totalPaginas(),
                    Duration.between(inicio, Instant.now()).toMillis());
        } catch (com.documind.processamento.DocumentProcessingService.CancelamentoProcessamentoException e) {
            LOG.info("Pipeline cancelado para documento {} (exclusao em andamento)", documentoId);
        } catch (InvalidFileException e) {
            falharDefinitivamente(documento, tarefa, e.getMessage());
        } catch (AiNotConfiguredException e) {
            falharDefinitivamente(documento, tarefa,
                    "Integracao de IA nao configurada: defina DOCUMIND_AI_* no ambiente e reprocesse o documento.");
        } catch (AiProviderException e) {
            boolean podeRetentar = tarefa.getTentativas() < TENTATIVAS_MAXIMAS;
            if (podeRetentar) {
                reagendarRetentativa(documento, tarefa);
            } else {
                falharDefinitivamente(documento, tarefa,
                        "Provedor de IA indisponivel apos varias tentativas.");
            }
        } catch (Exception e) {
            // Documento removido durante o processamento (FK violation etc.)
            LOG.error("Falha no processamento do documento {}", documentoId, e);
            if (!vectorSearchRepository.existeDocumento(documentoId)) {
                LOG.info("Documento {} foi excluido durante o processamento; abortando", documentoId);
                cancelamentosSolicitados.remove(documentoId);
                return;
            }
            falharDefinitivamente(documento, tarefa, "Falha inesperada no processamento.");
        }
    }

    /** Exclusao durante processamento: proximo lote aborta o trabalho. */
    public void solicitarCancelamento(Long documentoId) {
        cancelamentosSolicitados.add(documentoId);
    }

    private void verificarCancelamento(Long documentoId) {
        if (cancelamentosSolicitados.contains(documentoId) || !vectorSearchRepository.existeDocumento(documentoId)) {
            LOG.info("Processamento do documento {} cancelado", documentoId);
            cancelamentosSolicitados.remove(documentoId);
            throw new CancelamentoProcessamentoException();
        }
    }

    private void validarConfiguracaoIA() {
        if (!embeddingProvider.embeddingsDisponiveis()) {
            throw new AiNotConfiguredException();
        }
    }

    private void reagendarRetentativa(Documento documento, TarefaProcessamento tarefa) {
        tarefa.reenfileirar();
        tarefaRepository.save(tarefa);
        documento.setEstado(EstadoProcessamento.AGUARDANDO_PROCESSAMENTO);
        documento.setMensagemErro("Aguardando nova tentativa de processamento (provedor de IA temporariamente indisponivel).");
        documentoRepository.save(documento);
        long documentoId = documento.getId();
        reTentativas.schedule(() -> {
            emExecucao.add(documentoId);
            try {
                processar(documentoId);
            } catch (RuntimeException e) {
                LOG.error("Retentativa falhou de forma inesperada para documento {}", documentoId, e);
            } finally {
                emExecucao.remove(documentoId);
            }
        }, ATRASO_RE_TENTATIVA_SEGUNDOS, TimeUnit.SECONDS);
    }

    private void falharDefinitivamente(Documento documento, TarefaProcessamento tarefa, String mensagemSegura) {
        documento.setEstado(EstadoProcessamento.FALHOU);
        documento.setMensagemErro(truncar(mensagemSegura, 500));
        documentoRepository.save(documento);
        tarefa.marcarFalha(truncar(mensagemSegura, 500));
        tarefaRepository.save(tarefa);
        LOG.warn("Documento {} FALHOU: {}", documento.getId(), mensagemSegura);
    }

    private String metadadosJson(java.util.Map<String, Object> metadados) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadados);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "{}";
        }
    }

    private <T> List<List<T>> lotes(List<T> itens, int tamanho) {
        List<List<T>> lotes = new ArrayList<>();
        for (int inicio = 0; inicio < itens.size(); inicio += tamanho) {
            lotes.add(itens.subList(inicio, Math.min(itens.size(), inicio + tamanho)));
        }
        return lotes;
    }

    private String truncar(String texto, int maximo) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }

    /** Sinal interno de cancelamento (nao chega a falha de negocio). */
    static class CancelamentoProcessamentoException extends RuntimeException {
        CancelamentoProcessamentoException() {
            super("Processamento cancelado (documento excluido).");
        }
    }
}
