package com.documind.documento;

import com.documind.common.InvalidFileException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * Validacoes de upload: extensao permitida, tamanho, nome seguro e
 * assinatura do CONTEUDO (magic bytes) — o content-type do cliente nao
 * e confiavel e e sempre reconstruido pelo servidor.
 */
@Component
public class DocumentoUploadValidator {

    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of("pdf", "docx", "txt");

    /** Le os primeiros bytes para sniff de conteudo sem depender do cliente. */
    private static final int PEEK_TAMANHO = 1024;

    public ValidacaoUpload validar(InputStream conteudo, String nomeOriginalRecebido, long maxBytes) {
        String nomeSeguro = sanitizarNomeOriginal(nomeOriginalRecebido);
        String extensao = extensaoDe(nomeSeguro);
        if (!EXTENSOES_PERMITIDAS.contains(extensao)) {
            throw new InvalidFileException(
                    "Tipo de arquivo nao suportado (" + extensao.toUpperCase(Locale.ROOT) + "). Formatos aceitos: PDF, DOCX, TXT.");
        }
        if (maxBytes <= 0) {
            throw new InvalidFileException("Configuracao de tamanho maximo invalida.");
        }
        try {
            byte[] cabecalho = new byte[PEEK_TAMANHO];
            java.io.PushbackInputStream peek = new java.io.PushbackInputStream(conteudo, PEEK_TAMANHO);
            int lido = peek.read(cabecalho);
            if (lido <= 0) {
                throw new InvalidFileException("O arquivo enviado esta vazio.");
            }
            peek.unread(cabecalho, 0, lido);
            verificarAssinatura(extensao, cabecalho, Math.min(lido, PEEK_TAMANHO));
            return new ValidacaoUpload(nomeSeguro, extensao, mimeDe(extensao), maxBytes);
        } catch (IOException e) {
            throw new InvalidFileException("Falha ao ler o arquivo enviado.");
        }
    }

    private void verificarAssinatura(String extensao, byte[] bytes, int quantidade) {
        String normalizado = new String(java.util.Arrays.copyOf(bytes, quantidade), java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] pk = {0x50, 0x4B, 0x03, 0x04};

        switch (extensao) {
            case "pdf" -> {
                if (!normalizado.contains("%PDF-")) {
                    throw new InvalidFileException("O arquivo nao e um PDF valido (assinatura ausente).");
                }
            }
            case "docx" -> {
                if (quantidade < 4 || !comecaCom(bytes, pk)) {
                    throw new InvalidFileException("O arquivo nao e um DOCX valido (assinatura ausente).");
                }
            }
            case "txt" -> {
                for (int i = 0; i < Math.min(quantidade, 256); i++) {
                    if (bytes[i] == 0) {
                        throw new InvalidFileException("O arquivo parece binario e nao pode ser processado como TXT.");
                    }
                }
            }
            default -> throw new InvalidFileException("Extensao nao suportada.");
        }
    }

    private boolean comecaCom(byte[] origem, byte[] prefixo) {
        if (origem.length < prefixo.length) {
            return false;
        }
        for (int i = 0; i < prefixo.length; i++) {
            if (origem[i] != prefixo[i]) {
                return false;
            }
        }
        return true;
    }

    /** Somente o nome final; nada de caminhos. Remove controles/espacos perigosos. */
    String sanitizarNomeOriginal(String recebido) {
        String nome = recebido == null ? "arquivo" : recebido;
        int barraInvertida = nome.lastIndexOf('\\');
        int barra = nome.lastIndexOf('/');
        if (barraInvertida > -1) {
            nome = nome.substring(barraInvertida + 1);
        } else if (barra > -1) {
            nome = nome.substring(barra + 1);
        }
        nome = nome.replaceAll("[\\p{Cntrl}]+", "").trim();
        if (nome.isEmpty()) {
            nome = "arquivo";
        }
        return nome.length() > 260 ? nome.substring(0, 260) : nome;
    }

    private String extensaoDe(String nome) {
        int ponto = nome.lastIndexOf('.');
        if (ponto < 0 || ponto == nome.length() - 1) {
            throw new InvalidFileException("O arquivo nao possui extensao reconhecida.");
        }
        return nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    private String mimeDe(String extensao) {
        return switch (extensao) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "text/plain";
        };
    }

    public record ValidacaoUpload(String nomeOriginal, String extensao, String mime, long sizeHint) {
    }
}
