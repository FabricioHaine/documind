package com.documind.colecao;

import com.documind.common.BadRequestException;
import com.documind.common.ConflictException;
import com.documind.common.ForbiddenException;
import com.documind.common.NotFoundException;
import com.documind.documento.Documento;
import com.documind.documento.DocumentoRepository;
import com.documind.usuario.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Colecoes como ESCOPO de pesquisa: o RAG de uma conversa busca somente
 * nos documentos das colecoes autorizadas. Excluir a colecao NAO exclui
 * os documentos (desvinculacao via FK CASCADE apenas do vinculo) —
 * decisao de produto, documentada para o usuario.
 */
@Service
public class ColecaoService {

    private static final Logger LOG = LoggerFactory.getLogger(ColecaoService.class);

    private final ColecaoRepository colecaoRepository;
    private final DocumentoColecaoRepository documentoColecaoRepository;
    private final DocumentoRepository documentoRepository;

    public ColecaoService(ColecaoRepository colecaoRepository,
                          DocumentoColecaoRepository documentoColecaoRepository,
                          DocumentoRepository documentoRepository) {
        this.colecaoRepository = colecaoRepository;
        this.documentoColecaoRepository = documentoColecaoRepository;
        this.documentoRepository = documentoRepository;
    }

    // ------------------------------------------------------------------ //
    // CRUD                                                                //
    // ------------------------------------------------------------------ //

    @Transactional
    public ColecaoResponse criar(Usuario usuario, ColecaoRequest request) {
        String nome = request.nome().trim();
        if (colecaoRepository.existsByUsuarioIdAndNome(usuario.getId(), nome)) {
            throw new ConflictException("Ja existe uma colecao com este nome.");
        }
        Colecao colecao = new Colecao();
        colecao.setUsuario(usuario);
        colecao.setNome(nome);
        colecao.setDescricao(normalizarDescricao(request.descricao()));
        colecao = colecaoRepository.save(colecao);
        LOG.info("Colecao criada id={} usuario={}", colecao.getId(), usuario.getId());
        return paraResponse(colecao);
    }

    @Transactional(readOnly = true)
    public List<ColecaoResponse> listar(Usuario usuario) {
        return colecaoRepository.findByUsuarioIdOrderByDataAtualizacaoDesc(usuario.getId()).stream()
                .map(this::paraResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ColecaoDetalheResponse detalhar(Usuario usuario, Long id) {
        Colecao colecao = obterAutorizado(usuario, id);
        List<Long> documentoIds = documentoColecaoRepository.findByColecaoId(id).stream()
                .map(DocumentoColecao::getDocumentoId)
                .toList();
        List<ColecaoDetalheResponse.DocumentoDaColecao> documentos = documentoIds.isEmpty() ? List.of()
                : documentoRepository.findAllById(documentoIds).stream()
                        .map(ColecaoService::paraDocumentoDaColecao)
                        .sorted(Comparator.comparing(
                                (ColecaoDetalheResponse.DocumentoDaColecao d) -> d.dataUpload()).reversed())
                        .toList();
        return new ColecaoDetalheResponse(colecao.getId(), colecao.getNome(), colecao.getDescricao(),
                colecao.getDataCriacao(), colecao.getDataAtualizacao(), documentos.size(), documentos);
    }

    @Transactional
    public ColecaoResponse atualizar(Usuario usuario, Long id, ColecaoRequest request) {
        Colecao colecao = obterAutorizado(usuario, id);
        String nome = request.nome().trim();
        if (colecaoRepository.existsByUsuarioIdAndNomeExcluding(usuario.getId(), nome, id)) {
            throw new ConflictException("Ja existe uma colecao com este nome.");
        }
        colecao.setNome(nome);
        colecao.setDescricao(normalizarDescricao(request.descricao()));
        colecao = colecaoRepository.save(colecao);
        return paraResponse(colecao);
    }

    /**
     * Exclui apenas o CONTAINER. Documentos permanecem (regra do produto:
     * exclusao de colecao != exclusao de documentos). Conversas com escopo
     * nesta colecao ficam sem escopo (FK ON DELETE SET NULL) e passam a
     * buscar em todos os documentos do usuario.
     */
    @Transactional
    public void excluir(Usuario usuario, Long id) {
        Colecao colecao = obterAutorizado(usuario, id);
        colecaoRepository.delete(colecao);
        LOG.info("Colecao {} excluida pelo usuario {} (documentos preservados)", id, usuario.getId());
    }

    // ------------------------------------------------------------------ //
    // Documentos dentro da colecao                                        //
    // ------------------------------------------------------------------ //

    @Transactional
    public ColecaoDetalheResponse adicionarDocumento(Usuario usuario, Long colecaoId, Long documentoId) {
        Colecao colecao = obterAutorizado(usuario, colecaoId);
        Documento documento = documentoRepository.findWithUsuarioById(documentoId)
                .orElseThrow(() -> new NotFoundException("Documento nao encontrado."));
        if (!documento.pertenceA(usuario.getId())) {
            throw new ForbiddenException("Voce nao tem acesso a este documento.");
        }
        if (!documentoColecaoRepository.existsByColecaoIdAndDocumentoId(colecao.getId(), documento.getId())) {
            documentoColecaoRepository.save(DocumentoColecao.de(colecao.getId(), documento.getId()));
            LOG.info("Documento {} adicionado a colecao {}", documentoId, colecaoId);
        }
        return detalhar(usuario, colecaoId);
    }

    @Transactional
    public ColecaoDetalheResponse removerDocumento(Usuario usuario, Long colecaoId, Long documentoId) {
        Colecao colecao = obterAutorizado(usuario, colecaoId);
        boolean vinculoExiste = documentoColecaoRepository.existsByColecaoIdAndDocumentoId(colecao.getId(), documentoId);
        if (vinculoExiste) {
            documentoColecaoRepository.deleteByColecaoIdAndDocumentoId(colecao.getId(), documentoId);
            LOG.info("Documento {} removido da colecao {}", documentoId, colecaoId);
        }
        return detalhar(usuario, colecaoId);
    }

    // ------------------------------------------------------------------ //
    // Helpers                                                             //
    // ------------------------------------------------------------------ //

    private Colecao obterAutorizado(Usuario usuario, Long id) {
        Colecao colecao = colecaoRepository.findByIdAndUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new NotFoundException("Colecao nao encontrada."));
        if (!colecao.pertenceA(usuario.getId())) {
            throw new ForbiddenException("Voce nao tem acesso a esta colecao.");
        }
        return colecao;
    }

    private String normalizarDescricao(String descricao) {
        if (descricao == null) {
            return null;
        }
        String normalizada = descricao.trim();
        return normalizada.isEmpty() ? null : normalizada;
    }

    private ColecaoResponse paraResponse(Colecao colecao) {
        long quant = documentoColecaoRepository.countByColecaoId(colecao.getId());
        return new ColecaoResponse(colecao.getId(), colecao.getNome(), colecao.getDescricao(),
                colecao.getDataCriacao(), colecao.getDataAtualizacao(), quant);
    }

    private static ColecaoDetalheResponse.DocumentoDaColecao paraDocumentoDaColecao(Documento documento) {
        return new ColecaoDetalheResponse.DocumentoDaColecao(
                documento.getId(), documento.getNomeOriginal(), documento.getTipoArquivo(),
                documento.getTamanhoBytes(), documento.getTotalPaginas(),
                documento.getEstado().name(), documento.getDataUpload());
    }
}
