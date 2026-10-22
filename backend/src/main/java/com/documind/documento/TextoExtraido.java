package com.documind.documento;

import java.util.List;

/**
 * Texto extraido com informacao de pagina quando disponivel.
 * PDF: uma Pagina por pagina fisica. DOCX/TXT: pagina null (o formato nao
 * define paginas — limitacao documentada, nao prometemos o que nao existe).
 */
public record TextoExtraido(List<Pagina> paginas, Integer totalPaginas) {

    public record Pagina(Integer numero, String texto) {
    }

    public List<UnidadeTexto> unidades() {
        List<UnidadeTexto> unidades = new java.util.ArrayList<>();
        for (Pagina pagina : paginas) {
            for (String linha : pagina.texto().split("\n")) {
                String normalizada = normalizarLinha(linha);
                if (!normalizada.isEmpty()) {
                    unidades.add(new UnidadeTexto(pagina.numero(), normalizada));
                }
            }
        }
        return unidades;
    }

    /** Normalizacao leve: colapsa espacos, preserva significado. */
    static String normalizarLinha(String linha) {
        return linha == null ? "" : linha.replaceAll("[\\t\\x0B\\f\\r]+", " ").replaceAll(" {2,}", " ").trim();
    }

    public record UnidadeTexto(Integer pagina, String texto) {
    }
}
