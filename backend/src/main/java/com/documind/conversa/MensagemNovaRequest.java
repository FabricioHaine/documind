package com.documind.conversa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensagemNovaRequest(
        @NotBlank @Size(max = 4000) String conteudo
) {
}
