package com.documind.documento;

import com.documind.colecao.Colecao;
import com.documind.colecao.ColecaoRepository;
import com.documind.colecao.DocumentoColecao;
import com.documind.colecao.DocumentoColecaoRepository;
import com.documind.common.BadRequestException;
import com.documind.common.ConflictException;
import com.documind.common.ForbiddenException;
import com.documind.common.NotFoundException;
import com.documind.common.PageResponse;
import com.documind.common.RateLimitException;
import com.documind.config.AppProperties;
import com.documind.documento.storage.FileStorageService;
import com.documind.processamento.DocumentProcessingService;
import com.documind.processamento.EstadoTarefa;
import com.documind.processamento.TarefaProcessamento;
import com.documind.processamento.TarefaProcessamentoRepository;
import com.documind.usuario.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras de negocio de documentos: upload com validacao/armazenamento,
 * listagem com filtros, detalhe, download, exclusao consistente (cancelamento
 * de processamento + cascata no banco + remocao do arquivo) e reprocessamento.
 *
 * Isolamento: TODAS as operacoes exigem documento.usuarioId == usuario logado.
 */
@Service
public class DocumentoService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentoService.class);

    /** Registro de download para o controller montar a resposta HTTP. */
    public record DownloadArquivo(String nomeOriginal, String tipoMime, long tamanhoBytes, InputStream stream) {
    }

    private final DocumentoRepository documentoRepository;
    private final TrechoDocumentoRepository trechoRepository;
    private final DocumentoColecaoRepository documentoColecaoRepository;
    private final ColecaoRepository colecaoRepository;
    private final TarefaProcessamentoRepository tarefaRepository;
    private final FileStorageService storage;
    private final DocumentoUploadValidator validator;
    private final UploadRateLimiter rateLimiter;
    private final DocumentProcessingService processamento;
    private final AppProperties properties;

    public DocumentoService(DocumentoRepository documentoRepository,
                            TrechoDocumentoRepository trechoRepository,
                            DocumentoColecaoRepository documentoColecaoRepository,
                            ColecaoRepository colecaoRepository,
                            TarefaProcessamentoRepository tarefaRepository,
                            FileStorageService storage,
                            DocumentoUploadValidator validator,
                            UploadRateLimiter rateLimiter,
                            DocumentProcessingService processamento,
                            AppProperties properties) {
        this.documentoRepository = documentoRepository;
        this.trechoRepository = trechoRepository;
        this.documentoColecaoRepository = documentoColecaoRepository;
        this.colecaoRepository = colecaoRepository;
        this.tarefaRepository = tarefaRepository;
        this.storage = storage;
        this.validator = validator;
        this.rateLimiter = rateLimiter;
        this.processamento = processamento;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ //
    // Upload                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Valida e armazena cada arquivo, persiste metadados + tarefa e
     * enfileira o processamento assincrono (a resposta NAO espera o RAG).
     */
    @Transactional
    public List<DocumentoResponse> enviar(Usuario usuario, List<MultipartFile> arquivos, Long colecaoId) {
        if (arquivos == null || arquivos.isEmpty()) {
            throw new BadRequestException("Nenhum arquivo enviado.");
        }
        Colecao colecao = validarColecaoOpcional(usuario, colecaoId);

        long maxBytes = (long) properties.documents().maxSizeMb() * 1024 * 1024;
        List<DocumentoResponse> respostas = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            respostas.add(enviarUm(usuario, arquivo, colecao, maxBytes));
        }
        return respostas;
    }

    private DocumentoResponse enviarUm(Usuario usuario, MultipartFile arquivo, Colecao colecao, long maxBytes) {
        rateLimiter.checar(usuario.getId(), properties.documents().uploadsPerHour());

        DocumentoUploadValidator.ValidacaoUpload validacao;
        try (InputStream conteudo = arquivo.getInputStream()) {
            validacao = validator.validar(conteudo, arquivo.getOriginalFilename(), maxBytes);
        } catch (IOException e) {
            throw new BadRequestException("Falha ao ler o arquivo enviado.");
        }

        String nomeInterno;
        try (InputStream conteudo = arquivo.getInputStream()) {
            nomeInterno = storage.salvar(validacao.nomeOriginal(), conteudo, maxBytes);
        } catch (IOException e) {
            throw new BadRequestException("Falha ao ler o arquivo enviado.");
        }

        Documento documento = new Documento();
        documento.setUsuario(usuario);
        documento.setNomeOriginal(validacao.nomeOriginal());
        documento.setNomeArmazenamento(nomeInterno);
        documento.setTipoArquivo(validacao.extensao().toUpperCase(Locale.ROOT));
        documento.setTipoMime(validacao.mime());
        documento.setTamanhoBytes(arquivo.getSize());
        documento.setEstado(EstadoProcessamento.AGUARDANDO_PROCESSAMENTO);
        documento = documentoRepository.save(documento);

        if (colecao != null) {
            documentoColecaoRepository.save(DocumentoColecao.de(colecao.getId(), documento.getId()));
        }

        processamento.enfileirar(documento.getId());
        LOG.info("Upload documento id={} usuario={} tipo={} bytes={}",
                documento.getId(), usuario.getId(), documento.getTipoArquivo(), documento.getTamanhoBytes());

        List<DocumentoResponse.ColecaoResumo> colecoes = colecao == null
                ? List.of()
                : List.of(colecaoResumo(colecao));
        return paraResponse(documento, colecoes);
    }

    // ------------------------------------------------------------------ //
    // Listagem e detalhe                                                  //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public PageResponse<DocumentoResponse> listar(Usuario usuario, String nome, String tipo, String estado,
                                                  Long colecaoId, LocalDate data, Pageable pageable) {
        Specification<Documento> spec = DocumentoRepository.Specs.doUsuario(usuario.getId());
        if (nome != null && !nome.isBlank()) {
            spec = spec.and(DocumentoRepository.Specs.nomeContem(nome.trim()));
        }
        if (tipo != null && !tipo.isBlank()) {
            spec = spec.and(DocumentoRepository.Specs.tipoIgual(tipo.trim()));
        }
        if (estado != null && !estado.isBlank()) {
            try {
                spec = spec.and(DocumentoRepository.Specs.estadoIgual(
                        EstadoProcessamento.valueOf(estado.trim().toUpperCase(Locale.ROOT))));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Estado invalido: " + estado);
            }
        }
        if (colecaoId != null) {
            spec = spec.and(DocumentoRepository.Specs.colecao(colecaoId));
        }
        if (data != null) {
            spec = spec.and(DocumentoRepository.Specs.enviadoEm(data));
        }

        Pageable ordenado = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "dataUpload"));
        Page<Documento> pagina = documentoRepository.findAll(spec, ordenado);

        Map<Long, List<DocumentoResponse.ColecaoResumo>> colecoesPorDocumento =
                mapearColecoes(pagina.getContent());

        List<DocumentoResponse> conteudo = pagina.getContent().stream()
                .map(doc -> paraResponse(doc, colecoesPorDocumento.getOrDefault(doc.getId(), List.of())))
                .toList();
        return new PageResponse<>(conteudo, pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DocumentoDetalheResponse detalhe(Usuario usuario, Long id) {
        Documento documento = obterAutorizado(usuario, id);
        long trechos = trechoRepository.countByDocumentoId(id);
        TarefaProcessamento tarefa = tarefaRepository
                .findFirstByDocumentoIdOrderByDataCriacaoDesc(id)
                .orElse(null);

        return new DocumentoDetalheResponse(
                documento.getId(), documento.getNomeOriginal(), documento.getTipoArquivo(),
                documento.getTipoMime(), documento.getTamanhoBytes(), documento.getTotalPaginas(),
                documento.getEstado().name(), documento.getMensagemErro(), documento.getModeloEmbedding(),
                documento.getDataUpload(), documento.getDataProcessamento(), trechos,
                tarefa == null ? null : tarefa.getEstado().name(),
                tarefa == null ? null : tarefa.getTentativas(),
                tarefa == null ? null : tarefa.getUltimoErro(),
            colecoesDe(documento.getId()));
    }

    @Transactional(readOnly = true)
    public DocumentoStatusResponse status(Usuario usuario, Long id) {
        Documento documento = obterAutorizado(usuario, id);
        long trechos = trechoRepository.countByDocumentoId(id);
        return new DocumentoStatusResponse(documento.getId(), documento.getEstado().name(),
                documento.getMensagemErro(), trechos, documento.getDataProcessamento());
    }

    @Transactional(readOnly = true)
    public DownloadArquivo download(Usuario usuario, Long id) {
        Documento documento = obterAutorizado(usuario, id);
        return new DownloadArquivo(documento.getNomeOriginal(), documento.getTipoMime(),
                documento.getTamanhoBytes(), storage.abrir(documento.getNomeArmazenamento()));
    }

    // ------------------------------------------------------------------ //
    // Exclusao e reprocessamento                                          //
    // ------------------------------------------------------------------ //

    /**
     * Exclusao consistente:
     *  1. sinaliza cancelamento cooperativo (pipeline aborta no proximo lote);
     *  2. remove a linha do documento — FK ON DELETE CASCADE limpa trechos,
     *     embeddings (via trecho), tarefas e citacoes no proprio banco;
     *  3. remove o arquivo fisico (best-effort apos exclusao dos dados).
     * Trechos nunca ficam recuperaveis pelo RAG: a cascata e atomica no banco.
     */
    @Transactional
    public void excluir(Usuario usuario, Long id) {
        processamento.solicitarCancelamento(id);
        Documento documento = obterAutorizado(usuario, id);
        documentoRepository.delete(documento);
        documentoRepository.flush(); // garante cascata antes de mexer no disco
        storage.remover(documento.getNomeArmazenamento());
        LOG.info("Documento {} excluido pelo usuario {}", id, usuario.getId());
    }

    @Transactional
    public DocumentoStatusResponse reprocessar(Usuario usuario, Long id) {
        Documento documento = obterAutorizado(usuario, id);
        TarefaProcessamento tarefa = tarefaRepository
                .findFirstByDocumentoIdOrderByDataCriacaoDesc(id)
                .orElse(null);
        if (tarefa != null && tarefa.getEstado() == EstadoTarefa.EM_EXECUCAO) {
            throw new ConflictException(
                    "Processamento em andamento. Aguarde a conclusao antes de reprocessar.");
        }
        documento.setMensagemErro(null);
        documentoRepository.save(documento);
        processamento.enfileirar(id);
        return status(usuario, id);
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                             //
    // ------------------------------------------------------------------ //

    private Colecao validarColecaoOpcional(Usuario usuario, Long colecaoId) {
        if (colecaoId == null) {
            return null;
        }
        Colecao colecao = colecaoRepository.findById(colecaoId)
                .orElseThrow(() -> new NotFoundException("Colecao nao encontrada."));
        if (!colecao.pertenceA(usuario.getId())) {
            throw new ForbiddenException("Voce nao tem acesso a esta colecao.");
        }
        return colecao;
    }

    private Documento obterAutorizado(Usuario usuario, Long id) {
        Documento documento = documentoRepository.findWithUsuarioById(id)
                .orElseThrow(() -> new NotFoundException("Documento nao encontrado."));
        if (!documento.pertenceA(usuario.getId())) {
            throw new ForbiddenException("Voce nao tem acesso a este documento.");
        }
        return documento;
    }

    private Map<Long, List<DocumentoResponse.ColecaoResumo>> mapearColecoes(List<Documento> documentos) {
        if (documentos.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = documentos.stream().map(Documento::getId).toList();
        List<DocumentoColecao> vinculos = documentoColecaoRepository.findByDocumentoIdIn(ids);
        Map<Long, Colecao> colecoes = colecaoRepository.findAllById(
                        vinculos.stream().map(DocumentoColecao::getColecaoId).distinct().toList())
                .stream().collect(Collectors.toMap(Colecao::getId, Function.identity()));

        Map<Long, List<DocumentoResponse.ColecaoResumo>> resultado = new HashMap<>();
        for (DocumentoColecao vinculo : vinculos) {
            Colecao colecao = colecoes.get(vinculo.getColecaoId());
            if (colecao == null) {
                continue;
            }
            resultado.computeIfAbsent(vinculo.getDocumentoId(), k -> new ArrayList<>())
                    .add(new DocumentoResponse.ColecaoResumo(colecao.getId(), colecao.getNome()));
        }
        resultado.values().forEach(lista ->
                lista.sort(Comparator.comparing(DocumentoResponse.ColecaoResumo::nome,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))));
        return resultado;
    }

    private List<DocumentoResponse.ColecaoResumo> colecoesDe(Long documentoId) {
        List<DocumentoColecao> vinculos = documentoColecaoRepository.findByDocumentoId(documentoId);
        Map<Long, Colecao> colecoes = colecaoRepository.findAllById(
                        vinculos.stream().map(DocumentoColecao::getColecaoId).distinct().toList())
                .stream().collect(Collectors.toMap(Colecao::getId, Function.identity()));
        return vinculos.stream()
                .map(DocumentoColecao::getColecaoId)
                .map(colecoes::get)
                .filter(Objects::nonNull)
                .map(colecao -> new DocumentoResponse.ColecaoResumo(colecao.getId(), colecao.getNome()))
                .toList();
    }

    private DocumentoResponse.ColecaoResumo colecaoResumo(Colecao colecao) {
        if (colecao == null) {
            return null;
        }
        return new DocumentoResponse.ColecaoResumo(colecao.getId(), colecao.getNome());
    }

    private DocumentoResponse paraResponse(Documento documento, List<DocumentoResponse.ColecaoResumo> colecoes) {
        List<DocumentoResponse.ColecaoResumo> colecoesFiltradas = colecoes == null ? List.of()
                : colecoes.stream().filter(Objects::nonNull).toList();
        return new DocumentoResponse(
                documento.getId(), documento.getNomeOriginal(), documento.getTipoArquivo(),
                documento.getTipoMime(), documento.getTamanhoBytes(), documento.getTotalPaginas(),
                documento.getEstado().name(), documento.getMensagemErro(),
                documento.getDataUpload(), documento.getDataProcessamento(), colecoesFiltradas);
    }
}
