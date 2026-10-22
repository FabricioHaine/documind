package com.documind.common;

/**
 * Falha na chamada ao provedor de IA (rede, limite de requisicoes, resposta
 * invalida etc.). Nunca expoe detalhes internos ao usuario: o parametro
 * existe para contextualizacao em logs do chamador, nao na mensagem HTTP.
 */
public class AiProviderException extends ApiException {
    public AiProviderException(String detailSafe) {
        super(502, "PROVEDOR_IA_INDISPONIVEL",
                "O provedor de IA nao respondeu adequadamente. Tente novamente em instantes.");
    }
}
