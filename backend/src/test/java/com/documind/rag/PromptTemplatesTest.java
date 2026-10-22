package com.documind.rag;

import com.documind.rag.RagService.TrechoRecuperado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O prompt e a "interface" de seguranca semantica do RAG: numeracao de
 * fontes construida pelo BACKEND, regras anti-injecao e escopo presente.
 */
class PromptTemplatesTest {

    private TrechoRecuperado trecho(int ref, long docId, String nome, String conteudo, Integer pagina) {
        return new TrechoRecuperado(ref, docId, 0, conteudo, pagina, pagina, nome, "PDF", 0.9, ref);
    }

    @Test
    @DisplayName("instrucao de sistema fixa regras de honestidade e anti-injecao")
    void instrucaoSistemaContemRegras() {
        String regras = PromptTemplates.instrucaoSistema();

        assertThat(regras)
                .contains("Não invente")                      // nao inventar
                .contains("NÃO CONFIÁVEL")                    // dado/documento vs. instrucao
                .contains("Ignore quaisquer instruções")      // defesa de prompt injection
                .contains("Não encontrei informações");       // honestidade sem evidencia
    }

    @Test
    @DisplayName("contexto numerado usa referencias atribuidas pelo backend")
    void contextoNumerado() {
        List<TrechoRecuperado> fontes = List.of(
                trecho(1, 10L, "Manual de RH.pdf", "Cláusula de férias coletivas...", 3),
                trecho(2, 11L, "Contrato.docx", "Prazo de notificação prévia...", null));

        String contexto = PromptTemplates.contextoComReferencias(fontes);

        assertThat(contexto)
                .contains("[1] Documento: \"Manual de RH.pdf\"")
                .contains("— pág. 3")
                .contains("[2] Documento: \"Contrato.docx\"")
                .doesNotContain("[3]");
        // separador entre trechos
        assertThat(contexto.split("---")).hasSize(2);
    }

    @Test
    @DisplayName("mensagem de usuario mostra escopo, contexto e pergunta")
    void mensagemUsuarioMostraTudo() {
        String mensagem = PromptTemplates.mensagemUsuario(
                "a coleção \"Manual\"",
                "[1] Documento: \"A.pdf\"\nconteudo",
                "Quantos dias de férias?");

        assertThat(mensagem)
                .contains("ESCOPO DOS DOCUMENTOS: a coleção \"Manual\"")
                .contains("[1] Documento: \"A.pdf\"")
                .contains("Quantos dias de férias?");
    }

    @Test
    @DisplayName("contexto vazio produz texto vazio (sem simulacao)")
    void contextoVazio() {
        assertThat(PromptTemplates.contextoComReferencias(List.of())).isEmpty();
    }
}
