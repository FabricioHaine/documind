package com.documind.documento;

import com.documind.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Divisao do texto em trechos (chunks) para geração de embeddings.
 *
 * Estrategia: preserva unidades de linha/paragrafo com a pagina de origem,
 * acumula ate o tamanho configurado, aplica sobreposicao entre trechos
 * consecutivos e fatia por palavras quando uma linha e maior que o limite.
 * Nao divide "cegamente" por numero fixo de caracteres: respeita limites
 * de linha/paragrafo sempre que o extrator fornece estrutura.
 */
@Service
public class ChunkingService {

    private final AppProperties properties;

    public ChunkingService(AppProperties properties) {
        this.properties = properties;
    }

    public List<Pedaco> dividir(TextoExtraido texto) {
        int tamanho = Math.max(200, properties.rag().chunkSizeChars());
        int overlap = Math.min(tamanho / 2, Math.max(0, properties.rag().chunkOverlapChars()));

        List<Pedaco> pedacos = new ArrayList<>();
        List<TextoExtraido.UnidadeTexto> atuais = new ArrayList<>();
        int acumulado = 0;

        for (TextoExtraido.UnidadeTexto unidade : texto.unidades()) {
            for (TextoExtraido.UnidadeTexto fatia : fatiarUnidade(unidade, tamanho)) {
                int peso = fatia.texto().length() + 1;
                if (!atuais.isEmpty() && acumulado + peso > tamanho) {
                    pedacos.add(criarPedaco(atuais, pedacos.size()));
                    atuais = caudaDeOverlap(atuais, overlap);
                    acumulado = calcularTamanho(atuais);
                }
                atuais.add(fatia);
                acumulado += peso;
            }
        }
        if (!atuais.isEmpty()) {
            pedacos.add(criarPedaco(atuais, pedacos.size()));
        }
        return pedacos;
    }

    /** Quebra unidades maiores que o limite em pedacos por palavra. */
    private List<TextoExtraido.UnidadeTexto> fatiarUnidade(TextoExtraido.UnidadeTexto unidade, int tamanhoMaximo) {
        String texto = unidade.texto();
        if (texto.length() <= tamanhoMaximo) {
            return List.of(unidade);
        }
        List<TextoExtraido.UnidadeTexto> fatias = new ArrayList<>();
        int inicio = 0;
        while (inicio < texto.length()) {
            int fim = Math.min(inicio + tamanhoMaximo, texto.length());
            if (fim < texto.length()) {
                int espaco = texto.lastIndexOf(' ', fim);
                if (espaco > inicio + tamanhoMaximo / 2) {
                    fim = espaco;
                }
            }
            fatias.add(new TextoExtraido.UnidadeTexto(unidade.pagina(), texto.substring(inicio, fim).trim()));
            inicio = fim;
        }
        return fatias;
    }

    /** Mantem a cauda do trecho anterior (ate `overlap` caracteres) para preservar contexto. */
    private List<TextoExtraido.UnidadeTexto> caudaDeOverlap(List<TextoExtraido.UnidadeTexto> unidades, int overlap) {
        int alvo = Math.max(0, overlap) - 1; // desconta o "\n" do primeiro item mantido
        int tamanhoAcumulado = 0;
        int inicio = unidades.size();
        while (inicio > 0 && tamanhoAcumulado <= alvo) {
            inicio--;
            tamanhoAcumulado += unidades.get(inicio).texto().length() + 1;
        }
        return new ArrayList<>(unidades.subList(inicio, unidades.size()));
    }

    private int calcularTamanho(List<TextoExtraido.UnidadeTexto> unidades) {
        int tamanho = 0;
        for (var unidade : unidades) {
            tamanho += unidade.texto().length() + 1;
        }
        return tamanho;
    }

    private Pedaco criarPedaco(List<TextoExtraido.UnidadeTexto> unidades, int indice) {
        StringBuilder conteudo = new StringBuilder();
        Integer paginaInicio = null;
        Integer paginaFim = null;
        for (var unidade : unidades) {
            if (unidade.pagina() != null) {
                if (paginaInicio == null) {
                    paginaInicio = unidade.pagina();
                }
                paginaFim = unidade.pagina();
            }
            conteudo.append(unidade.texto()).append('\n');
        }
        String textoFinal = conteudo.toString().trim();
        return new Pedaco(indice, textoFinal, paginaInicio, paginaFim,
                Map.of("caracteres", textoFinal.length(), "unidades", unidades.size()));
    }

    /** Trecho pronto para indexacao. */
    public record Pedaco(int indice, String conteudo, Integer paginaInicio, Integer paginaFim,
                         Map<String, Object> metadados) {
    }
}
