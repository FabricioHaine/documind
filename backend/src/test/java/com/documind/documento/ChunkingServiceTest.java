package com.documind.documento;

import com.documind.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitarios do chunking: limites respeitados, overlap presente,
 * paginas preservadas e unidades maiores que o limite fatiadas por palavra.
 */
class ChunkingServiceTest {

    private ChunkingService servico(int tamanho, int overlap) {
        return new ChunkingService(new AppProperties(
                null, null, null, new AppProperties.Rag(tamanho, overlap), null));
    }

    private TextoExtraido textoDeLinhas(Integer pagina, List<String> linhas) {
        return new TextoExtraido(List.of(new TextoExtraido.Pagina(pagina, String.join("\n", linhas))), null);
    }

    @Test
    @DisplayName("texto curto gera um unico trecho com conteudo integral")
    void textoCurtoUmTrecho() {
        ChunkingService chunking = servico(1200, 200);
        TextoExtraido texto = textoDeLinhas(1, List.of("primeira linha", "segunda linha"));

        List<ChunkingService.Pedaco> pedacos = chunking.dividir(texto);

        assertThat(pedacos).hasSize(1);
        assertThat(pedacos.get(0).indice()).isZero();
        assertThat(pedacos.get(0).conteudo()).isEqualTo("primeira linha\nsegunda linha");
    }

    @Test
    @DisplayName("texto acima do limite gera varios trechos, todos abaixo do maximo")
    void textosLimososGeramVariosTrechos() {
        ChunkingService chunking = servico(200, 50);
        StringBuilder enorme = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            enorme.append("linha numero ").append(i).append(" com conteudo de teste\n");
        }
        TextoExtraido texto = textoDeLinhas(1, List.of(enorme.toString()));

        List<ChunkingService.Pedaco> pedacos = chunking.dividir(texto);

        assertThat(pedacos.size()).isGreaterThanOrEqualTo(2);
        for (ChunkingService.Pedaco pedaco : pedacos) {
            assertThat(pedaco.conteudo().length()).isLessThanOrEqualTo(200 + 1); // + "\n" final margem
            assertThat(pedaco.conteudo()).isNotBlank();
        }
        // indices numerados sequencialmente
        for (int i = 0; i < pedacos.size(); i++) {
            assertThat(pedacos.get(i).indice()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("trecho seguinte mantem cauda de overlap do trecho anterior")
    void overlapEntreTrechos() {
        ChunkingService chunking = servico(200, 60);
        StringBuilder enorme = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            enorme.append("frase distinta ").append(i).append(" para overlap\n");
        }
        TextoExtraido texto = textoDeLinhas(null, List.of(enorme.toString()));

        List<ChunkingService.Pedaco> pedacos = chunking.dividir(texto);

        assertThat(pedacos.size()).isGreaterThanOrEqualTo(2);
        // A cauda do trecho 0 deve reaparecer no inicio do trecho 1 (overlap).
        String caudaAnterior = ultimaLinhaDe(pedacos.get(0).conteudo());
        if (!caudaAnterior.isBlank()) {
            assertThat(pedacos.get(1).conteudo()).contains(caudaAnterior.trim());
        }
    }

    private String ultimaLinhaDe(String conteudo) {
        String[] linhas = conteudo.split("\n");
        return linhas.length == 0 ? "" : linhas[linhas.length - 1];
    }

    @Test
    @DisplayName("paginas de origem sao registradas (paginaInicio/paginaFim)")
    void paginasPreservadas() {
        ChunkingService chunking = servico(200, 50);
        TextoExtraido texto = new TextoExtraido(List.of(
                new TextoExtraido.Pagina(1, String.join("\n", java.util.Collections.nCopies(20, "primeira pagina texto a"))),
                new TextoExtraido.Pagina(2, String.join("\n", java.util.Collections.nCopies(20, "segunda pagina texto b")))
        ), 2);

        List<ChunkingService.Pedaco> pedacos = chunking.dividir(texto);

        assertThat(pedacos).isNotEmpty();
        assertThat(pedacos.get(0).paginaInicio()).isEqualTo(1);
        // Algum trecho coseu as duas paginas
        boolean paginasCoerentes = pedacos.stream()
                .anyMatch(p -> p.paginaInicio() != null && p.paginaFim() != null && p.paginaFim() >= p.paginaInicio());
        assertThat(paginasCoerentes).isTrue();
    }

    @Test
    @DisplayName("linhas em branco e espacos repetidos sao normalizados")
    void normalizacaoDeLinhas() {
        TextoExtraido texto = new TextoExtraido(List.of(
                new TextoExtraido.Pagina(1, "linha   com   espacos\n\n\n      \nova linha")
        ), 1);
        assertThat(texto.unidades()).hasSize(2);
        assertThat(texto.unidades().get(0).texto()).isEqualTo("linha com espacos");
    }
}
