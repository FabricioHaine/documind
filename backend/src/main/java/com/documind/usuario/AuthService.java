package com.documind.usuario;

import com.documind.common.BadRequestException;
import com.documind.common.ConflictException;
import com.documind.common.ApiException;
import com.documind.security.JwtService;
import com.documind.security.AuthFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Cadastro e autenticacao de usuarios. Senhas sao armazenadas apenas como
 * hash BCrypt; credenciais invalidas geram sempre a MESMA mensagem (sem
 * revelar se o e-mail existe).
 */
@Service
public class AuthService {

    /** Mensagem unica para credenciais invalidas (evita enumeracao de e-mails). */
    static final String CREDENCIAS_INVALIDAS = "E-mail ou senha invalidos.";

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_AUTH_CODE = "CREDENCIAIS_INVALIDAS";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthFacade authFacade;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthFacade authFacade) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authFacade = authFacade;
    }

    @Transactional
    public TokenResponse registrar(RegistroUsuarioRequest request) {
        String emailNormalizado = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ConflictException("Este e-mail ja esta cadastrado.");
        }
        if (!senhaAtendePolitica(request.senha())) {
            throw new BadRequestException("A senha deve ter ao menos 8 caracteres, com letras e numeros.");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(request.nome().trim());
        usuario.setEmail(emailNormalizado);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));

        usuarioRepository.save(usuario);
        LOG.info("Usuario cadastrado id={}", usuario.getId());

        return emitirToken(usuario);
    }

    public TokenResponse login(LoginRequest request) {
        String emailNormalizado = request.email().trim().toLowerCase();
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(emailNormalizado)
                .filter(Usuario::isAtivo)
                .filter(u -> passwordEncoder.matches(request.senha(), u.getSenhaHash()))
                .orElseThrow(() -> failingCredentials());

        return emitirToken(usuario);
    }

    public UsuarioResponse me() {
        Usuario usuario = authFacade.currentUser();
        return UsuarioResponse.from(usuario);
    }

    private boolean senhaAtendePolitica(String senha) {
        return senha != null && senha.length() >= 8 && senha.chars().anyMatch(Character::isLetter)
                && senha.chars().anyMatch(Character::isDigit);
    }

    private ApiException failingCredentials() {
        return new ApiException(401, GENERIC_AUTH_CODE, CREDENCIAS_INVALIDAS);
    }

    private TokenResponse emitirToken(Usuario usuario) {
        String token = jwtService.generate(usuario.getId(), usuario.getEmail());
        Instant expiracao = Instant.now().plus(jwtService.lifetime());
        return new TokenResponse(token, TokenResponse.BEARER, expiracao, UsuarioResponse.from(usuario));
    }
}
