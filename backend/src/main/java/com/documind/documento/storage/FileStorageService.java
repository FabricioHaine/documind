package com.documind.documento.storage;

import com.documind.common.InvalidFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Armazenamento local de arquivos com nomes internos gerados pela aplicacao
 * (o nome original do arquivo NUNCA vira caminho fisico). Todas as leituras
 * passam por uma travanca que impede escape do diretorio raiz.
 */
@Service
public class FileStorageService {

    private final Path raiz;

    public FileStorageService(@Value("${app.storage.path}") String caminhoBase) {
        this.raiz = Path.of(caminhoBase).toAbsolutePath().normalize();
    }

    /** Garante criacao do diretorio (lazy, para nao travar testes unitarios). */
    public void preparar() {
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel criar o diretorio de armazenamento: " + raiz, e);
        }
    }

    /** Salva o conteudo e devolve o nome interno gerado. */
    public String salvar(String nomeOriginalSeguro, InputStream conteudo, long maxBytes) {
        preparar();
        String nomeInterno = gerarNomeInterno(nomeOriginalSeguro);
        Path destino = resolver(nomeInterno);
        try {
            long total = Files.copy(conteudo, destino);
            if (total > maxBytes) {
                remover(nomeInterno);
                throw new InvalidFileException("O arquivo excede o tamanho maximo permitido.");
            }
            if (total == 0) {
                remover(nomeInterno);
                throw new InvalidFileException("O arquivo enviado esta vazio.");
            }
            return nomeInterno;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao armazenar o arquivo.", e);
        }
    }

    public Path resolver(String nomeArmazenamento) {
        Path alvo = raiz.resolve(nomeArmazenamento).normalize();
        if (!alvo.startsWith(raiz)) {
            throw new IllegalArgumentException("Nome de arquivo interno invalido.");
        }
        return alvo;
    }

    public InputStream abrir(String nomeArmazenamento) {
        try {
            return Files.newInputStream(resolver(nomeArmazenamento));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao abrir arquivo armazenado.", e);
        }
    }

    public void remover(String nomeArmazenamento) {
        try {
            Files.deleteIfExists(resolver(nomeArmazenamento));
        } catch (IOException e) {
            // Exclusao best-effort: metadados ja foram removidos do banco.
            org.slf4j.LoggerFactory.getLogger(FileStorageService.class)
                    .warn("Falha ao remover arquivo {} do disco", nomeArmazenamento);
        }
    }

    private String gerarNomeInterno(String nomeOriginalSeguro) {
        String extensao = "";
        int ponto = nomeOriginalSeguro.lastIndexOf('.');
        if (ponto >= 0) {
            String bruta = nomeOriginalSeguro.substring(ponto).toLowerCase(Locale.ROOT);
            if (bruta.length() <= 6 && bruta.matches("\\.[a-z0-9]+")) {
                extensao = bruta;
            }
        }
        return UUID.randomUUID() + extensao;
    }

    public Path caminhoRaiz() {
        return raiz;
    }
}
