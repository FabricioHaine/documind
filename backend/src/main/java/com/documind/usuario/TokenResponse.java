package com.documind.usuario;

import java.time.Instant;

public record TokenResponse(String token, String tipo, Instant expiraEm, UsuarioResponse usuario) {
    public static final String BEARER = "Bearer";
}
