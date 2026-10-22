package com.documind.documento;

import com.documind.common.InvalidFileException;
import org.springframework.stereotype.Service;

/**
 * Escolhe o extrator compativel com o tipo do arquivo (detectado pela
 * extensao validada e confirmado pela assinatura de conteudo).
 */
@Service
public class TextExtractionService {

    private final TextExtractor[] extratores;

    public TextExtractionService(TextExtractor... extratores) {
        this.extratores = extratores;
    }

    public TextoExtraido extrair(java.nio.file.Path arquivo, String tipoArquivo) {
        for (TextExtractor extrator : extratores) {
            if (extrator.suporta(tipoArquivo)) {
                return extrator.extrair(arquivo);
            }
        }
        throw new InvalidFileException("Formato sem extrator configurado.");
    }
}
