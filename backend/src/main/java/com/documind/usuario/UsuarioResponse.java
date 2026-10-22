package com.documind.usuario;

import java.time.Instant;

/** Representacao segura do usuario: NUNCA inclui hash de senha. */
public record UsuarioResponse(Long id, String nome, String email, Instant dataCriacao) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getDataCriacao());
    }
}
