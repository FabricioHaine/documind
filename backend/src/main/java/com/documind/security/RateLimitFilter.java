package com.documind.security;

import com.documind.config.AppProperties;
import com.documind.common.RateLimitException;
import com.documind.usuario.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite simples de taxa (janela fixa de 1 minuto) para operacoes caras ou
 * sensiveis:
 * - POST /api/auth/** (mitigacao de forca bruta, por IP)
 * - POST /api/conversations/{id}/messages (chamadas de IA, por usuario)
 * - POST /api/search/semantic (vetores + IA, por usuario)
 *
 * Limitacao documentada: contadores em memoria (por instância), adequados ao MVP.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_POST_PATHS = Set.of("/api/auth/register", "/api/auth/login");
    private final AppProperties properties;
    private final Map<String, Window> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean limitAuth = "POST".equals(method) && AUTH_POST_PATHS.contains(path);
        boolean limitMessages = "POST".equals(method) && path.matches("/api/conversations/\\d+/messages");
        boolean limitSearch = "POST".equals(method) && "/api/search/semantic".equals(path);

        if (limitAuth || limitMessages || limitSearch) {
            String key = privateKey(request, limitAuth);
            if (key != null && !tryAcquire(key)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"status\":429,\"code\":\"LIMITE_EXCEDIDO\",\"message\":"
                        + "\"Muitas requisicoes. Aguarde um momento antes de tentar novamente.\""
                        + ",\"violations\":[]}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String privateKey(HttpServletRequest request, boolean byIp) {
        if (byIp) {
            return "ip:" + request.getRemoteAddr();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Usuario usuario) {
            return "user:" + usuario.getId();
        }
        return null;
    }

    private boolean tryAcquire(String key) {
        int limit = Math.max(1, properties.security().rateLimitPerMinute());
        long nowMinute = System.currentTimeMillis() / 60_000;
        Window window = counters.compute(key, (k, existing) ->
                existing != null && existing.minute == nowMinute ? existing : new Window(nowMinute));
        return window.count.incrementAndGet() <= limit;
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
