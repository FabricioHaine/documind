package com.documind.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emisso e validacao de tokens JWT (HMAC-SHA2). O algoritmo exato e
 * escolhido pelo jjwt conforme o tamanho do segredo: >= 32 caracteres
 * garante no minimo HS256; com segredos longos (ex.: 64 chars) resulta HS512.
 *
 * O segredo vem exclusivamente da variavel de ambiente DOCUMIND_JWT_SECRET.
 * Se estiver ausente ou curta demais, a aplicacao NAO inicia (fail fast),
 * pois um segredo fraco tornaria todos os tokens forjaveis.
 */
@Component
public class JwtService {

    private static final Logger LOG = LoggerFactory.getLogger(JwtService.class);
    private static final int SECRET_MIN_LENGTH = 32;

    private final SecretKey secretKey;
    private final Duration expiration;

    public JwtService(@Value("${DOCUMIND_JWT_SECRET:}") String jwtSecret,
                      @Value("${app.security.jwt-exp-minutes:120}") int jwtExpMinutes) {
        if (jwtSecret == null || jwtSecret.length() < SECRET_MIN_LENGTH) {
            throw new IllegalStateException(
                    "Variavel de ambiente DOCUMIND_JWT_SECRET obrigatoria (minimo "
                    + SECRET_MIN_LENGTH + " caracteres). Nao commite segredos no codigo.");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(jwtExpMinutes);
    }

    public String generate(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(secretKey)
                .compact();
    }

    public Instant expirationOf(String token) {
        return parse(token).map(Claims::getExpiration)
                .map(d -> d.toInstant())
                .orElseThrow(() -> new JwtException("Token sem expiracao"));
    }

    public Duration lifetime() {
        return expiration;
    }

    /** Retorna o id do usuario se o token for valido; vazio caso contrario. */
    public Optional<Long> validateAndGetUserId(String token) {
        return parse(token)
                .filter(claims -> {
                    Date expirationDate = claims.getExpiration();
                    return expirationDate != null && expirationDate.after(new Date());
                })
                .map(Claims::getSubject)
                .map(subject -> {
                    try {
                        return Long.parseLong(subject);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
    }

    private Optional<Claims> parse(String token) {
        try {
            var jws = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return Optional.of(jws.getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            LOG.debug("Token JWT invalido: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
