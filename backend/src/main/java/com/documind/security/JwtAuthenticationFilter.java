package com.documind.security;

import com.documind.usuario.Usuario;
import com.documind.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extrai o token Bearer da requisicao, valida a assinatura/expiracao e
 * carrega o usuario real do banco. Falha silenciosa (sem autenticacao) para
 * rotas publicas; rotas protegidas sao bloqueadas pelo SecurityFilterChain.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            jwtService.validateAndGetUserId(token).ifPresent(userId ->
                    usuarioRepository.findById(userId)
                            .filter(Usuario::isAtivo)
                            .ifPresent(usuario -> {
                                var authentication = new UsernamePasswordAuthenticationToken(
                                        usuario, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }));
        }

        filterChain.doFilter(request, response);
    }
}
