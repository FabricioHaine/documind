package com.documind.documento;

import com.documind.common.InvalidFileException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Extracao de texto de DOCX (Apache POI). Inclui texto de tabelas — permance
 * relevante para contratos. O formato DOCX nao define paginas fixas, portanto
 * a numeracao de paginas NAO e disponivel (limitacao documentada).
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public boolean suporta(String tipoArquivo) {
        return "DOCX".equalsIgnoreCase(tipoArquivo);
    }

    @Override
    public TextoExtraido extrair(Path arquivo) {
        try (InputStream input = Files.newInputStream(arquivo);
             XWPFDocument document = new XWPFDocument(input)) {

            StringBuilder texto = new StringBuilder();
            for (XWPFParagraph paragrafo : document.getParagraphs()) {
                String conteudo = paragrafo.getText();
                texto.append(conteudo == null ? "" : conteudo.trim()).append("\n");
            }
            for (XWPFTable tabela : document.getTables()) {
                for (XWPFTableRow linha : tabela.getRows()) {
                    for (var celula : linha.getTableCells()) {
                        texto.append(celula.getText().trim()).append("\t");
                    }
                    texto.append("\n");
                }
            }

            if (texto.toString().isBlank()) {
                throw new InvalidFileException("O DOCX nao contem texto extraivel.");
            }
            return new TextoExtraido(List.of(new TextoExtraido.Pagina(null, texto.toString())), null);
        } catch (IOException e) {
            throw new InvalidFileException("DOCX invalido ou corrompido.");
        }
    }
}
