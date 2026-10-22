package com.documind.documento;

import com.documind.common.InvalidFileException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracao de texto de PDFs (PDFBox 3, pagina por pagina).
 * NOTA: PDFs digitalizados/imagem nao produzem texto — recebem mensagem clara.
 * PDFs protegidos por senha falham com mensagem explicita.
 */
@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean suporta(String tipoArquivo) {
        return "PDF".equalsIgnoreCase(tipoArquivo);
    }

    @Override
    public TextoExtraido extrair(java.nio.file.Path arquivo) {
        try (PDDocument document = Loader.loadPDF(arquivo.toFile())) {
            if (document.isEncrypted()) {
                throw new InvalidFileException("O PDF esta protegido por senha e nao pode ser processado.");
            }
            int totalPaginas = document.getNumberOfPages();
            List<TextoExtraido.Pagina> paginas = new ArrayList<>();
            for (int numero = 1; numero <= totalPaginas; numero++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(numero);
                stripper.setEndPage(numero);
                stripper.setSortByPosition(true);
                stripper.setLineSeparator("\n");
                String texto = stripper.getText(document);
                paginas.add(new TextoExtraido.Pagina(numero, texto == null ? "" : texto));
            }
            boolean semTexto = paginas.stream().allMatch(p -> p.texto().isBlank());
            if (semTexto) {
                throw new InvalidFileException(
                        "O PDF nao contem texto extraivel (provavelmente digitalizado ou apenas imagens).");
            }
            return new TextoExtraido(paginas, totalPaginas);
        } catch (IOException e) {
            throw new InvalidFileException("PDF invalido ou corrompido.");
        }
    }
}
