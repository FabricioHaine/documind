package com.documind.usuario;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacao. Sem regra de negocio aqui: delega ao AuthService.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um novo usuario e retorna o token")
    public TokenResponse register(@Valid @RequestBody RegistroUsuarioRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e retorna o token JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuario autenticado")
    public UsuarioResponse me() {
        return authService.me();
    }
}
