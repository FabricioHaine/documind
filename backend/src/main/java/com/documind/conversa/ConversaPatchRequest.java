package com.documind.conversa;

import jakarta.validation.constraints.Size;

/** PATCH parcial: campos nulos nao sao alterados. */
public record ConversaPatchRequest(
        @Size(max = 160) String titulo,
        Long colecaoId
) {
}
