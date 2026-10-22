package com.documind.documento;

/** Extrator especializado por formato. */
public interface TextExtractor {

    boolean suporta(String tipoArquivo);

    TextoExtraido extrair(java.nio.file.Path arquivo);
}
