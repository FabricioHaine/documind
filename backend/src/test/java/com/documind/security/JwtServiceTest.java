package com.documind.security;

import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tokens: segredo forte obrigatorio (fail fast), roundtrip de validacao,
 * segredo diferente invalida e expiracao considerada.
 */
class JwtServiceTest {

    private static final String SEGREDO = "segredo-local-de-teste-Com-Mais-de-32-Bytes!!";

    private JwtService servico(String segredo, int minutos) {
        return new JwtService(segredo, minutos);
    }

    @Test
    @DisplayName("segredo curto impede inicio do servico (fail fast)")
    void segredoCurtoRejeitado() {
        assertThatThrownBy(() -> servico("curto", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMIND_JWT_SECRET");
    }

    @Test
    @DisplayName("token emitido valida e devolve o id do usuario")
    void roundtrip() {
        JwtService jq = servico(SEGREDO, 30);

        String token = jq.generate(42L, "ana@documind.dev");

        assertThat(jq.validateAndGetUserId(token)).contains(42L);
        assertThat(jq.expirationOf(token))
                .isAfter(Instant.now().plus(java.time.Duration.ofMinutes(28)));
        assertThat(jq.lifetime()).isEqualTo(java.time.Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("token assinado por outro segredo nao valida")
    void segredoDiferenteInvalida() {
        JwtService emissor = servico(SEGREDO, 30);
        String token = emissor.generate(1L, "x@y.z");

        JwtService outro = new JwtService("outro-segredo-bem-mais-longo-que-32-chars-para-teste", 30);
        assertThat(outro.validateAndGetUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("token expirado nao valida")
    void expiradoInvalido() {
        JwtService jq = servico(SEGREDO, 30);
        String tokenExpirado = tokenComExpiracaoNoPassado();
        assertThat(jq.validateAndGetUserId(tokenExpirado)).isEmpty();
    }

    private String tokenComExpiracaoNoPassado() {
        SecretKey chave = Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8));
        return io.jsonwebtoken.Jwts.builder()
                .subject("7")
                .expiration(Date.from(Instant.now().minusSeconds(120)))
                .signWith(chave)
                .compact();
    }

    @Test
    @DisplayName("lixo/nao-JWT nao valida mas nao lanca")
    void tokenLixo() {
        JwtService jq = servico(SEGREDO, 30);
        assertThat(jq.validateAndGetUserId("nao-e-jwt")).isEmpty();
        assertThat(jq.validateAndGetUserId("")).isEmpty();
        assertThat(jq.validateAndGetUserId(null)).isEmpty();
    }
}
