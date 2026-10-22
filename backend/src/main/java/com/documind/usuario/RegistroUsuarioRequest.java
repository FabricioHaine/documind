package com.documind.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada de cadastro. A confirmacao de senha e validada no front-end
 * (Reactive Forms); no backend aplica-se a politica real de senha.
 */
public record RegistroUsuarioRequest(
        @NotBlank @Size(min = 2, max = 120) String nome,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).*$",
                 message = "A senha deve conter pelo menos uma letra e um numero")
        String senha
) {
}
