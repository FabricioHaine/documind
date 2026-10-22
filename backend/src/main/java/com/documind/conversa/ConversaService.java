package com.documind.conversa;

import com.documind.colecao.Colecao;
import com.documind.colecao.ColecaoRepository;
import com.documind.common.AiNotConfiguredException;
import com.documind.common.AiProviderException;
import com.documind.common.ForbiddenException;
import com.documind.common.NotFoundException;
import com.documind.documento.Documento;
import com.documind.documento.DocumentoRepository;
import com.documind.rag.RagService;
import com.documind.usuario.Usuario;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Conversas com documentos. Fluxo de uma pergunta:
 *  1. valida usuario + acesso a conversa/colecao;
 *  2. persiste a pergunta;
 *  3. retrieval vetorial com isolamento (RagService -> pgvector);
 *  4. geracao da resposta com contexto, OU mensagem honesta de sem evidencia;
 *  5. persiste resposta (estado OK/SEM_EVIDENCIA/ERRO) + citacoes snapshot;
 *  6. retorna resposta + fontes.
 *
 * A decisao de "sem evidencia" e do BACKEND (recuperou vazio). Falha de
 * geracao persiste mensagem com estado ERRO — nunca se persiste uma
 * resposta incompleta como concluida sem registro.
 */
@Service
public class ConversaService {

    private static final Logger LOG = LoggerFactory.getLogger(ConversaService.class);
    private static final int TITULO_MAXIMO = 60;

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final CitacaoRepository citacaoRepository;
    private final ColecaoRepository colecaoRepository;
    private final DocumentoRepository documentoRepository;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    public ConversaService(ConversaRepository conversaRepository,
                           MensagemRepository mensagemRepository,
                           CitacaoRepository citacaoRepository,
                           ColecaoRepository colecaoRepository,
                           DocumentoRepository documentoRepository,
                           RagService ragService,
                           ObjectMapper objectMapper) {
        this.conversaRepository = conversaRepository;
        this.mensagemRepository = mensagemRepository;
        this.citacaoRepository = citacaoRepository;
        this.colecaoRepository = colecaoRepository;
        this.documentoRepository = documentoRepository;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ //
    // CRUD                                                                //
    // ------------------------------------------------------------------ //

    @Transactional
    public ConversaResponse criar(Usuario usuario, ConversaNovaRequest request) {
        Conversa conversa = new Conversa();
        conversa.setUsuario(usuario);
        conversa.setColecao(validarColecaoOpcional(usuario, request.colecaoId()));
        if (request.titulo() != null && !request.titulo().isBlank()) {
            conversa.setTitulo(truncar(request.titulo().trim(), 160));
        }
        conversa = conversaRepository.save(conversa);
        return paraResponse(conversa);
    }

    @Transactional(readOnly = true)
    public List<ConversaResponse> listar(Usuario usuario) {
        return conversaRepository.findByUsuarioIdOrderByDataAtualizacaoDesc(usuario.getId()).stream()
                .map(this::paraResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversaResponse detalhar(Usuario usuario, Long id) {
        return paraResponse(obterAutorizado(usuario, id));
    }

    @Transactional
    public ConversaResponse atualizar(Usuario usuario, Long id, ConversaPatchRequest request) {
        Conversa conversa = obterAutorizado(usuario, id);
        if (request.titulo() != null && !request.titulo().isBlank()) {
            conversa.setTitulo(truncar(request.titulo().trim(), 160));
        }
        if (request.colecaoId() != null) {
            conversa.setColecao(validarColecaoOpcional(usuario, request.colecaoId()));
        }
        conversa.tocar();
        return paraResponse(conversaRepository.save(conversa));
    }

    @Transactional
    public void excluir(Usuario usuario, Long id) {
        Conversa conversa = obterAutorizado(usuario, id);
        conversaRepository.delete(conversa);
    }

    // ------------------------------------------------------------------ //
    // Mensagens                                                           //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public List<MensagemResponse> listarMensagens(Usuario usuario, Long conversaId) {
        Conversa conversa = obterAutorizado(usuario, conversaId);
        List<Mensagem> mensagens = mensagemRepository.findByConversaIdOrderByDataCriacaoAsc(conversa.getId());
        List<Long> mensagemIds = mensagens.stream().map(Mensagem::getId).toList();
        Map<Long, List<Citacao>> citacoesPorMensagem = mensagemIds.isEmpty() ? Map.of()
                : citacaoRepository.findByMensagemIdIn(mensagemIds).stream()
                        .collect(Collectors.groupingBy(Citacao::getMensagemId));
        Map<Long, String> nomesDocumento = mapearNomesDocumento(citacoesPorMensagem);

        return mensagens.stream()
                .map(m -> paraMensagemResponse(m, citacoesPorMensagem.getOrDefault(m.getId(), List.of()), nomesDocumento))
                .toList();
    }

    /** Pergunta real passando pelo pipeline RAG (nunca resposta simulada). */
    @Transactional
    public MensagemResponse enviarMensagem(Usuario usuario, Long conversaId, MensagemNovaRequest request) {
        Conversa conversa = obterAutorizado(usuario, conversaId);
        String pergunta = request.conteudo().trim();

        Mensagem perguntaObj = Mensagem.perguntaUsuario(conversa.getId(), pergunta);
        perguntaObj = mensagemRepository.save(perguntaObj);

        if (Conversa.TITULO_PADRAO.equals(conversa.getTitulo())) {
            conversa.setTitulo(truncar(pergunta, TITULO_MAXIMO));
        }
        conversa.tocar();
        conversaRepository.save(conversa);

        String escopo = escopoDescricao(conversa);
        try {
            List<RagService.TrechoRecuperado> trechos = ragService.recuperar(
                    usuario.getId(), conversa.getColecao() == null ? null : conversa.getColecao().getId(), pergunta);
            boolean semEvidencia = trechos.isEmpty();
            RagService.Resposta resposta = ragService.gerar(pergunta, escopo, trechos, semEvidencia);

            Mensagem.EstadoMensagem estado = resposta.semEvidencia()
                    ? Mensagem.EstadoMensagem.SEM_EVIDENCIA
                    : Mensagem.EstadoMensagem.OK;

            Mensagem respostaObj = Mensagem.respostaAssistente(
                    conversa.getId(), resposta.texto(), estado, metadadosJson(resposta, trechos.size()));
            respostaObj = mensagemRepository.save(respostaObj);

            List<Citacao> citacoes = persistirCitacoes(respostaObj, resposta.fontes());
            LOG.info("RAG respondido conversa={} usuario={} estado={} fontes={}",
                    conversaId, usuario.getId(), estado, citacoes.size());

            return paraMensagemResponse(respostaObj, citacoes,
                    mapearNomesDocumento(Map.of(respostaObj.getId(), citacoes)));
        } catch (AiNotConfiguredException e) {
            // Config pendente: mensagem CLARA, sem simular resposta.
            throw e;
        } catch (AiProviderException e) {
            persistirErroAssistente(conversa.getId(),
                    "Não foi possível gerar a resposta agora (falha temporária do provedor de IA). Tente novamente.");
            throw e;
        } catch (Exception e) {
            LOG.error("Falha inesperada ao responder conversa={} usuario={}", conversaId, usuario.getId(), e);
            persistirErroAssistente(conversa.getId(),
                    "Não foi possível gerar a resposta agora. Tente novamente.");
            throw new AiProviderException(null);
        }
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                             //
    // ------------------------------------------------------------------ //

    private void persistirErroAssistente(Long conversaId, String mensagemSegura) {
        try {
            mensagemRepository.save(Mensagem.respostaAssistente(
                    conversaId, mensagemSegura, Mensagem.EstadoMensagem.ERRO, "{}"));
        } catch (Exception ex) {
            LOG.error("Falha ao registrar mensagem de erro da conversa {}", conversaId, ex);
        }
    }

    private List<Citacao> persistirCitacoes(Mensagem respostaObj, List<RagService.TrechoRecuperado> fontes) {
        if (fontes == null || fontes.isEmpty()) {
            return List.of();
        }
        List<Citacao> citacoes = new ArrayList<>();
        for (RagService.TrechoRecuperado fonte : fontes) {
            Citacao citacao = new Citacao();
            citacao.setMensagemId(respostaObj.getId());
            citacao.setDocumentoId(fonte.documentoId());
            citacao.setTrechoDocumentoId(fonte.trechoId());
            citacao.setReferencia(fonte.referencia());
            citacao.setIndiceTrecho(fonte.indiceTrecho());
            citacao.setPaginaInicio(fonte.paginaInicio());
            citacao.setPaginaFim(fonte.paginaFim());
            citacao.setConteudoTrecho(fonte.conteudo());
            citacoes.add(citacao);
        }
        return citacaoRepository.saveAll(citacoes);
    }

    private Map<Long, String> mapearNomesDocumento(Map<Long, List<Citacao>> citacoesPorMensagem) {
        List<Long> documentoIds = citacoesPorMensagem.values().stream()
                .flatMap(List::stream)
                .map(Citacao::getDocumentoId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (documentoIds.isEmpty()) {
            return Map.of();
        }
        return documentoRepository.findAllById(documentoIds).stream()
                .collect(Collectors.toMap(Documento::getId, Documento::getNomeOriginal));
    }

    private String escopoDescricao(Conversa conversa) {
        return conversa.getColecao() == null
                ? "todos os documentos concluídos do seu acervo"
                : "a coleção \"" + conversa.getColecao().getNome() + "\"";
    }

    private String metadadosJson(RagService.Resposta resposta, int trechosContexto) {
        Map<String, Object> metadados = new HashMap<>();
        metadados.put("modelo", resposta.modelo());
        if (resposta.promptTokens() != null) {
            metadados.put("tokensEntrada", resposta.promptTokens());
        }
        if (resposta.completionTokens() != null) {
            metadados.put("tokensSaida", resposta.completionTokens());
        }
        metadados.put("latenciaMs", resposta.latenciaMs());
        metadados.put("trechosContexto", trechosContexto);
        try {
            return objectMapper.writeValueAsString(metadados);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Colecao validarColecaoOpcional(Usuario usuario, Long colecaoId) {
        if (colecaoId == null) {
            return null;
        }
        Colecao colecao = colecaoRepository.findByIdAndUsuarioId(colecaoId, usuario.getId())
                .orElseThrow(() -> new NotFoundException("Colecao nao encontrada."));
        if (!colecao.pertenceA(usuario.getId())) {
            throw new ForbiddenException("Voce nao tem acesso a esta colecao.");
        }
        return colecao;
    }

    private Conversa obterAutorizado(Usuario usuario, Long id) {
        Conversa conversa = conversaRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new NotFoundException("Conversa nao encontrada."));
        if (!conversa.pertenceA(usuario.getId())) {
            throw new ForbiddenException("Voce nao tem acesso a esta conversa.");
        }
        return conversa;
    }

    private ConversaResponse paraResponse(Conversa conversa) {
        Colecao colecao = conversa.getColecao();
        return new ConversaResponse(conversa.getId(), conversa.getTitulo(),
                colecao == null ? null : colecao.getId(),
                colecao == null ? null : colecao.getNome(),
                conversa.getDataCriacao(), conversa.getDataAtualizacao());
    }

    private MensagemResponse paraMensagemResponse(Mensagem mensagem, List<Citacao> citacoes,
                                                  Map<Long, String> nomesDocumento) {
        Map<String, Object> metadados;
        try {
            metadados = objectMapper.readValue(
                    mensagem.getMetadados(), new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            metadados = Map.of();
        }
        List<CitacaoResponse> citacaoResponses = citacoes.stream()
                .map(c -> new CitacaoResponse(c.getId(), c.getDocumentoId(),
                        c.getDocumentoId() == null ? null : nomesDocumento.get(c.getDocumentoId()),
                        c.getTrechoDocumentoId(), c.getReferencia(), c.getIndiceTrecho(),
                        c.getPaginaInicio(), c.getPaginaFim(), c.getConteudoTrecho()))
                .toList();
        return new MensagemResponse(mensagem.getId(), mensagem.getAutor().name(),
                mensagem.getEstado().name(), mensagem.getConteudo(), mensagem.getDataCriacao(),
                metadados, citacaoResponses);
    }

    private String truncar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
