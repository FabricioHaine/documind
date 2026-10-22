package com.documind.busca;

import com.documind.common.PageResponse;
import com.documind.documento.DocumentoResponse;
import com.documind.rag.RagService;
import com.documind.security.AuthFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pesquisa global:
 * - GET  /api/search          → filtrar documentos por metadados (nome/tipo/estado/data/colecao);
 * - POST /api/search/semantic → trechos por significado via busca vetorial
 *   com MESMO isolamento do chat (restricted ao usuario e escopo).
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Pesquisa")
public class BuscaController {

    private final DocumentoBuscaAdapter documentoBusca;
    private final RagService ragService;
    private final AuthFacade authFacade;

    public BuscaController(DocumentoBuscaAdapter documentoBusca, RagService ragService, AuthFacade authFacade) {
        this.documentoBusca = documentoBusca;
        this.ragService = ragService;
        this.authFacade = authFacade;
    }

    @GetMapping
    @Operation(summary = "Pesquisa por metadados de documento (nome, tipo, estado, colecao, data)")
    public com.documind.common.PageResponse<DocumentoResponse> buscar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long colecaoId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate data,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentoBusca.listar(authFacade.currentUser(), nome, tipo, estado, colecaoId, data,
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100)));
    }

    @PostMapping("/semantic")
    @Operation(summary = "Busca semantica de trechos (requer integracao de embeddings configurada)")
    public BuscaSemanticaResponse buscarSemantico(@Valid @RequestBody BuscaSemanticaRequest request) {
        var usuario = authFacade.currentUser();
        List<RagService.TrechoRecuperado> trechos;
        try {
            trechos = ragService.recuperar(usuario.getId(), request.colecaoId(), request.pergunta());
        } catch (com.documind.common.AiNotConfiguredException e) {
            throw new com.documind.common.ApiException(503, "IA_NAO_CONFIGURADA",
                    "A busca semantica exige integracao de embeddings configurada "
                    + "(DOCUMIND_AI_BASE_URL e DOCUMIND_AI_EMBEDDING_MODEL).");
        }
        List<RagService.TrechoRecuperado> numerados = ragService.numerarReferencias(trechos);
        return new BuscaSemanticaResponse(request.pergunta(), request.colecaoId(),
                numerados.stream().map(BuscaSemanticaResponse.TrechoHit::de).toList());
    }

    public record BuscaSemanticaRequest(
            @NotBlank @Size(max = 2000) String pergunta,
            Long colecaoId
    ) {
    }

    public record BuscaSemanticaResponse(String pergunta, Long colecaoId, List<TrechoHit> resultados) {

        public record TrechoHit(
                long trechoId,
                long documentoId,
                String documentoNome,
                String documentoTipo,
                int indiceTrecho,
                Integer paginaInicio,
                Integer paginaFim,
                String conteudo,
                double similaridade,
                int referencia
        ) {
            static TrechoHit de(RagService.TrechoRecuperado t) {
                return new TrechoHit(t.trechoId(), t.documentoId(), t.documentoNome(), t.documentoTipo(),
                        t.indiceTrecho(), t.paginaInicio(), t.paginaFim(), t.conteudo(),
                        t.similaridade(), t.referencia());
            }
        }
    }
}
