package com.documind.conversa;

import jakarta.validation.constraints.Size;

public record ConversaNovaRequest(
        @Size(max = 160) String titulo,
        Long colecaoId
) {
}
