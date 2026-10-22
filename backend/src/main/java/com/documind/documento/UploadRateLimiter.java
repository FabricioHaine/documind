package com.documind.documento;

import com.documind.common.RateLimitException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Janela deslizante simples (1 hora) por usuario para evitar uploads
 * excessivos. Contadores em memoria — limitacao documentada do MVP.
 */
@Component
public class UploadRateLimiter {

    private final Map<Long, Deque<Instant>> enviosPorUsuario = new ConcurrentHashMap<>();

    public void checar(Long usuarioId, int maximoPorHora) {
        int limite = Math.max(1, maximoPorHora);
        Deque<Instant> janela = enviosPorUsuario.computeIfAbsent(usuarioId, k -> new ArrayDeque<>());
        Instant limiteInferior = Instant.now().minus(Duration.ofHours(1));
        synchronized (janela) {
            janela.removeIf(instant -> instant.isBefore(limiteInferior));
            if (janela.size() >= limite) {
                throw new RateLimitException(
                        "Limite de " + limite + " envios por hora atingido. Tente novamente mais tarde.");
            }
            janela.addLast(Instant.now());
        }
    }
}
