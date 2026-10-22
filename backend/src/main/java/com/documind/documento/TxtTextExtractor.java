package com.documind.documento;

import com.documind.common.InvalidFileException;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Extracao de texto de arquivos TXT (UTF-8, com fallback Windows-1252).
 */
@Component
public class TxtTextExtractor implements TextExtractor {

    @Override
    public boolean suporta(String tipoArquivo) {
        return "TXT".equalsIgnoreCase(tipoArquivo);
    }

    @Override
    public TextoExtraido extrair(Path arquivo) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(arquivo);
        } catch (java.io.IOException e) {
            throw new InvalidFileException("Falha ao ler o arquivo TXT.");
        }
        if (bytes.length == 0) {
            throw new InvalidFileException("O arquivo TXT esta vazio.");
        }

        String texto = new String(bytes, Charset.forName("UTF-8"));
        if (texto.contains("\uFFFD")) {
            texto = decodificarWindows1252(bytes);
        }
        if (texto.isBlank()) {
            throw new InvalidFileException("O arquivo TXT nao contem texto.");
        }
        return new TextoExtraido(List.of(new TextoExtraido.Pagina(null, texto)), null);
    }

    private String decodificarWindows1252(byte[] bytes) {
        try {
            return new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        } catch (Exception e) {
            throw new InvalidFileException("Encoding do arquivo TXT nao suportado.");
        }
    }
}
