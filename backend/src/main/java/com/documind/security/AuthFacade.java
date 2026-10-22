package com.documind.security;

import com.documind.usuario.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acesso ao usuario autenticado a partir do contexto de seguranca.
 * O userId NUNCA vem do front-end: sempre do token validado pelo filtro JWT.
 */
@Component
public class AuthFacade {

    public Usuario currentUser() {
        Usuario principal = authenticatedUser();
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("Sem usuário autenticado");
        }
        return principal;
    }

    public Long currentUserId() {
        Usuario user = authenticatedUser();
        return user == null ? null : user.getId();
    }

    public boolean isAuthenticated() {
        return authenticatedUser() != null;
    }

    private Usuario authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        return null;
    }
}
