package com.documind.rag;

import com.documind.config.AiProperties;
import com.documind.rag.client.ChatMessage;
import com.documind.rag.client.ChatProvider;
import com.documind.rag.client.ChatResult;
import com.documind.rag.client.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Orquestra a parte de IA do RAG:
 *  1. embedding da pergunta;
 *  2. busca vetorial com isolamento (delegada ao VectorSearchRepository);
 *  3. construcao do contexto numerado;
 *  4. geracao da resposta com instrucoes endurecidas;
 *  5. metadados de uso (latencia, tokens) para observabilidade - sem segredos.
 */
@Service
public class RagService {

    private static final Logger LOG = LoggerFactory.getLogger(RagService.class);

    public record TrechoRecuperado(long trechoId, long documentoId, int indiceTrecho,
                                   String conteudo, Integer paginaInicio, Integer paginaFim,
                                   String documentoNome, String documentoTipo,
                                   double similaridade, int referencia) {
    }

    public record Resposta(String texto, List<TrechoRecuperado> fontes, boolean semEvidencia,
                           String modelo, Integer promptTokens, Integer completionTokens,
                           long latenciaMs) {
    }

    public static final String MENSAGEM_SEM_EVIDENCIA =
            "Não encontrei informações suficientes nos documentos selecionados para responder com segurança.";

    private final AiProperties properties;
    private final EmbeddingProvider embeddingProvider;
    private final ChatProvider chatProvider;
    private final VectorSearchRepository vectorSearchRepository;

    public RagService(AiProperties properties, EmbeddingProvider embeddingProvider,
                      ChatProvider chatProvider, VectorSearchRepository vectorSearchRepository) {
        this.properties = properties;
        this.embeddingProvider = embeddingProvider;
        this.chatProvider = chatProvider;
        this.vectorSearchRepository = vectorSearchRepository;
    }

    /** Recupera os trechos mais relevantes do escopo (usuario/colecao). */
    public List<TrechoRecuperado> recuperar(long usuarioId, Long colecaoId, String pergunta) {
        if (!properties.embeddingsConfigured()) {
            throw new com.documind.common.AiNotConfiguredException();
        }
        Instant inicio = Instant.now();
        float[] vetorPergunta = embeddingProvider.embedBatch(List.of(pergunta)).get(0);
        int limite = Math.max(1, properties.maxContextChunks());
        double limiar = properties.minRelevanceThreshold();

        List<VectorSearchRepository.TrechoRanqueado> ranqueados =
                vectorSearchRepository.buscaPorSimilaridade(
                        usuarioId, colecaoId,
                        properties.embeddingModel(),
                        Vetores.paraLiteral(vetorPergunta),
                        limite, limiar);

        LOG.debug("RAG retrieval usuario={} colecao={} trechos={} em {} ms",
                usuarioId, colecaoId, ranqueados.size(), Duration.between(inicio, Instant.now()).toMillis());

        return ranqueados.stream()
                .map(t -> new TrechoRecuperado(t.trechoId(), t.documentoId(), t.indiceTrecho(),
                        t.conteudo(), t.paginaInicio(), t.paginaFim(),
                        t.documentoNome(), t.documentoTipo(), t.similaridade(), 0))
                .toList();
    }

    /**
     * Gera a resposta com o contexto. Se o modelo ignorar as regras e responder
     * mesmo sem contexto, o estado SEM_EVIDENCIA continua `false` — por isso a
     * decisao de "sem evidencia" e do BACKEND (quando recuperacao vazia).
     */
    public Resposta gerar(String pergunta, String escopoDescricao,
                          List<TrechoRecuperado> trechos, boolean semEvidenciaAntes) {
        if (semEvidenciaAntes) {
            return new Resposta(MENSAGEM_SEM_EVIDENCIA, List.of(), true,
                    properties.chatModel(), null, null, 0);
        }
        if (!chatProvider.chatDisponivel()) {
            throw new com.documind.common.AiNotConfiguredException();
        }
        Instant inicio = Instant.now();
        // Numeracao [1..n] vem do BACKEND e e usada no prompt e nas citacoes.
        List<TrechoRecuperado> fontes = numerarReferencias(trechos);
        String contexto = PromptTemplates.contextoComReferencias(fontes);
        List<ChatMessage> mensagens = List.of(
                ChatMessage.sistema(PromptTemplates.instrucaoSistema()),
                ChatMessage.usuario(PromptTemplates.mensagemUsuario(escopoDescricao, contexto, pergunta)));

        ChatResult resultado = chatProvider.gerar(mensagens);
        long latencia = Duration.between(inicio, Instant.now()).toMillis();
        LOG.info("RAG generation latenciaMs={} tokens={}/{}", latencia,
                resultado.promptTokens(), resultado.completionTokens());

        return new Resposta(resultado.content(), fontes, false,
                properties.chatModel(), resultado.promptTokens(), resultado.completionTokens(), latencia);
    }

    /** Atribui numeros [1..n] as fontes na ordem da recuperacao. */
    public List<TrechoRecuperado> numerarReferencias(List<TrechoRecuperado> trechos) {
        List<TrechoRecuperado> numerados = new java.util.ArrayList<>(trechos.size());
        for (int i = 0; i < trechos.size(); i++) {
            TrechoRecuperado trecho = trechos.get(i);
            numerados.add(new TrechoRecuperado(trecho.trechoId(), trecho.documentoId(), trecho.indiceTrecho(),
                    trecho.conteudo(), trecho.paginaInicio(), trecho.paginaFim(), trecho.documentoNome(),
                    trecho.documentoTipo(), trecho.similaridade(), i + 1));
        }
        return numerados;
    }

    public Optional<String> modeloAtual() {
        return properties.isConfigured() ? Optional.of(properties.chatModel()) : Optional.empty();
    }
}
