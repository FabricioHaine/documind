package com.documind.documento;

import com.documind.common.InvalidFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validacao de upload baseada em CONTEUDO (magic bytes), nunca no
 * content-type do cliente. Os testes cobrem extensao, assinatura,
 * nome com caminho e arquivo vazio.
 */
class DocumentoUploadValidatorTest {

    private final DocumentoUploadValidator validator = new DocumentoUploadValidator();

    private InputStream fluxo(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static byte[] cabecalhoPdf() {
        return ("%PDF-1.7\n" + "x".repeat(300)).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static byte[] cabecalhoPkJpegContra() {
        byte[] pk = {0x50, 0x4B, 0x03, 0x04};
        byte[] resto = "conteudo falso".getBytes();
        byte[] tudo = new byte[pk.length + resto.length];
        System.arraycopy(pk, 0, tudo, 0, pk.length);
        System.arraycopy(resto, 0, tudo, pk.length, resto.length);
        return tudo;
    }

    @Test
    @DisplayName("PDF com assinatura correta passa e devolve mime application/pdf")
    void pdfValido() {
        var resultado = validator.validar(fluxo(cabecalhoPdf()), "relatorio.pdf", 1024 * 1024);

        assertThat(resultado.extensao()).isEqualTo("pdf");
        assertThat(resultado.mime()).isEqualTo("application/pdf");
        assertThat(resultado.nomeOriginal()).isEqualTo("relatorio.pdf");
    }

    @Test
    @DisplayName("extensao com caixa mista e normalizada; mime TXT correto")
    void extensaoNormalizada() {
        var resultado = validator.validar(
                fluxo("texto normal sem bytes nulos".getBytes()), "NOTAS.TXT", 1024);

        assertThat(resultado.extensao()).isEqualTo("txt");
        assertThat(resultado.mime()).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("extensao proibida e rejeitada")
    void extensaoProibida() {
        assertThatThrownBy(() -> validator.validar(fluxo(new byte[]{(byte) 0xFF, (byte) 0xD8}), "foto.jpg", 1024))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("nao suportado");
    }

    @Test
    @DisplayName("arquivo sem extensao e rejeitado")
    void semExtensao() {
        assertThatThrownBy(() -> validator.validar(fluxo(cabecalhoPdf()), "semextensao", 1024))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("extensao");
    }

    @Test
    @DisplayName("conteudo com extensao .pdf mas sem assinatura %PDF e rejeitado (sniff)")
    void pdfSemAssinatura() {
        byte[] falso = "arquivo que mente sobre ser pdf".getBytes();
        assertThatThrownBy(() -> validator.validar(fluxo(falso), "falso.pdf", 1024))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("assinatura");
    }

    @Test
    @DisplayName(".docx exige magic bytes PK")
    void docxExigePk() {
        assertThatThrownBy(() -> validator.validar(fluxo("nao e zip".getBytes()), "falso.docx", 1024))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("DOCX");

        var ok = validator.validar(fluxo(cabecalhoPkJpegContra()), "valido.docx", 1024);
        assertThat(ok.extensao()).isEqualTo("docx");
    }

    @Test
    @DisplayName("TXT com bytes nulos (binario) e rejeitado")
    void txtBinario() {
        byte[] binario = new byte[300];
        binario[10] = 0;
        assertThatThrownBy(() -> validator.validar(fluxo(binario), "binario.txt", 1024))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("binario");
    }

    @Test
    @DisplayName("arquivo vazio e rejeitado")
    void arquivoVazio() {
        assertThatThrownBy(() -> validator.validar(fluxo(new byte[0]), "vazio.pdf", 1024))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    @DisplayName("nome original sanitizado: caminho e controles removidos")
    void nomeSanitizado() {
        var resultado = validator.validar(
                fluxo("txt ok".getBytes()), "..\\..\\pasta\\me Arq uivo\t(1).txt", 1024);
        assertThat(resultado.nomeOriginal()).isEqualTo("me Arq uivo(1).txt");
    }

    @Test
    @DisplayName("nome nulo recebe valor padrao")
    void nomeNuloPadronizado() {
        String sane = validator.sanitizarNomeOriginal(null);
        assertThat(sane).isEqualTo("arquivo");
    }

    @Test
    @DisplayName("nome longo e truncado para 260 chars")
    void nomeLongoTruncado() {
        String longo = "a".repeat(400) + ".pdf";
        assertThat(validator.sanitizarNomeOriginal(longo)).hasSize(260);
    }
}
