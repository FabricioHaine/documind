package com.documind.colecao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ColecaoRequest(
        @NotBlank @Size(min = 1, max = 120) String nome,
        @Size(max = 500) String descricao
) {
}
