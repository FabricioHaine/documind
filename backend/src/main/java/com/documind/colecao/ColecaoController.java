package com.documind.colecao;

import com.documind.security.AuthFacade;
import com.documind.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collections")
@Tag(name = "Coleções")
public class ColecaoController {

    private final ColecaoService service;
    private final AuthFacade authFacade;

    public ColecaoController(ColecaoService service, AuthFacade authFacade) {
        this.service = service;
        this.authFacade = authFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma colecao (escopo de pesquisa para o RAG)")
    public ColecaoResponse criar(@Valid @RequestBody ColecaoRequest request) {
        return service.criar(authFacade.currentUser(), request);
    }

    @GetMapping
    @Operation(summary = "Lista as colecoes do usuario")
    public List<ColecaoResponse> listar() {
        return service.listar(authFacade.currentUser());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da colecao com seus documentos")
    public ColecaoDetalheResponse detalhar(@PathVariable Long id) {
        return service.detalhar(authFacade.currentUser(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Renomeia ou edita a descricao")
    public ColecaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ColecaoRequest request) {
        return service.atualizar(authFacade.currentUser(), id, request);
    }

    @PostMapping("/{id}/documents")
    @Operation(summary = "Adiciona um documento a colecao")
    public ColecaoDetalheResponse adicionarDocumento(@PathVariable Long id,
                                                     @RequestBody Map<String, Long> body) {
        Long documentoId = body == null ? null : body.get("documentoId");
        return service.adicionarDocumento(authFacade.currentUser(), id, documentoId);
    }

    @DeleteMapping("/{id}/documents/{documentoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove o documento da colecao (documento permanece)")
    public void removerDocumento(@PathVariable Long id, @PathVariable Long documentoId) {
        service.removerDocumento(authFacade.currentUser(), id, documentoId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a colecao; documentos NAO sao excluidos")
    public void excluir(@PathVariable Long id) {
        service.excluir(authFacade.currentUser(), id);
    }
}
