package com.documind.conversa;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversas")
public class ConversaController {

    private final ConversaService service;
    private final AuthFacade authFacade;

    public ConversaController(ConversaService service, AuthFacade authFacade) {
        this.service = service;
        this.authFacade = authFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma conversa (opcionalmente com escopo de colecao)")
    public ConversaResponse criar(@Valid @RequestBody(required = false) ConversaNovaRequest request) {
        return service.criar(authFacade.currentUser(),
                request == null ? new ConversaNovaRequest(null, null) : request);
    }

    @GetMapping
    @Operation(summary = "Lista as conversas do usuario")
    public List<ConversaResponse> listar() {
        return service.listar(authFacade.currentUser());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhe da conversa")
    public ConversaResponse detalhar(@PathVariable Long id) {
        return service.detalhar(authFacade.currentUser(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Altera titulo e/ou escopo da colecao")
    public ConversaResponse atualizar(@PathVariable Long id, @Valid @RequestBody ConversaPatchRequest request) {
        return service.atualizar(authFacade.currentUser(), id, request);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Historico completo de mensagens com citacoes")
    public List<MensagemResponse> listarMensagens(@PathVariable Long id) {
        return service.listarMensagens(authFacade.currentUser(), id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia uma pergunta e recebe resposta fundamentada com fontes")
    public MensagemResponse enviarMensagem(@PathVariable Long id,
                                           @Valid @RequestBody MensagemNovaRequest request) {
        return service.enviarMensagem(authFacade.currentUser(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui conversa, mensagens e citacoes")
    public void excluir(@PathVariable Long id) {
        service.excluir(authFacade.currentUser(), id);
    }
}
