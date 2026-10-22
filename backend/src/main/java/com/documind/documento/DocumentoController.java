package com.documind.documento;

import com.documind.common.PageResponse;
import com.documind.security.AuthFacade;
import com.documind.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de documentos. Sem regra de negocio: delega ao DocumentoService.
 * O usuario SEMPRE vem do contexto de seguranca (AuthFacade) — nunca do front.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documentos")
public class DocumentoController {

    private final DocumentoService service;
    private final AuthFacade authFacade;

    public DocumentoController(DocumentoService service, AuthFacade authFacade) {
        this.service = service;
        this.authFacade = authFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia um ou mais documentos (PDF/DOCX/TXT) e enfileira o processamento")
    public List<DocumentoResponse> upload(@RequestParam("arquivos") List<MultipartFile> arquivos,
                                          @RequestParam(required = false) Long colecaoId) {
        Usuario usuario = authFacade.currentUser();
        return service.enviar(usuario, arquivos, colecaoId);
    }

    @GetMapping
    @Operation(summary = "Lista documentos com filtros, ordenacao e paginacao")
    public PageResponse<DocumentoResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long colecaoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Usuario usuario = authFacade.currentUser();
        return service.listar(usuario, nome, tipo, estado, colecaoId, data,
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe do documento (metadados, trechos e tarefa)")
    public DocumentoDetalheResponse detalhe(@PathVariable Long id) {
        return service.detalhe(authFacade.currentUser(), id);
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Estado do processamento (para polling no front-end)")
    public DocumentoStatusResponse status(@PathVariable Long id) {
        return service.status(authFacade.currentUser(), id);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Baixa o arquivo original armazenado")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        DocumentoService.DownloadArquivo arquivo = service.download(authFacade.currentUser(), id);
        String nomeSeguro = URLEncoder.encode(arquivo.nomeOriginal(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + nomeSeguro)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(arquivo.tamanhoBytes()))
                .body(new InputStreamResource(arquivo.stream()));
    }

    @PostMapping("/{id}/reprocess")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reprocessa o documento (extracao, chunks, embeddings)")
    public DocumentoStatusResponse reprocessar(@PathVariable Long id) {
        return service.reprocessar(authFacade.currentUser(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui documento, trechos, embeddings, tarefas e arquivo fisico")
    public void excluir(@PathVariable Long id) {
        service.excluir(authFacade.currentUser(), id);
    }
}
