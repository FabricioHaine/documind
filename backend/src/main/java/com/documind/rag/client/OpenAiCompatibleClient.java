package com.documind.rag.client;

import com.documind.config.AiProperties;
import com.documind.common.AiNotConfiguredException;
import com.documind.common.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Provedor de IA compativel com a API OpenAI (/chat/completions, /embeddings).
 *
 * Funciona com Ollama (http://localhost:11434/v1), vLLM, OpenRouter, Azure
 * (com gateway compativel) etc. O provedor/modelo e configurado por ambiente:
 * nenhum modelo fica fixo no codigo.
 *
 * Resiliencia:
 * - 401/403 (credenciais) e respostas malformadas: NAO se retentam.
 * - 429 (limite), 5xx e erros temporarios: ate DOCUMIND_AI_MAX_RETRIES
 *   tentativas com backoff exponencial curto.
 */
@Component
public class OpenAiCompatibleClient implements EmbeddingProvider, ChatProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AiProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleClient(AiProperties properties, RestClient aiRestClient) {
        this.properties = properties;
        this.restClient = aiRestClient;
    }

    // ------------------------------------------------------------------ //
    // Embeddings                                                          //
    // ------------------------------------------------------------------ //

    @Override
    public List<float[]> embedBatch(List<String> textos) {
        garantirEmbeddingsConfigurados();
        String url = joinearUrl("/embeddings");
        String autorizacao = comAutorizacao();
        var corpo = new java.util.LinkedHashMap<String, Object>();
        corpo.put("model", properties.embeddingModel());
        corpo.put("input", textos);
        if (properties.embeddingDimensions() > 0) {
            corpo.put("dimensions", properties.embeddingDimensions());
        }

        EmbeddingResposta resposta = comRetentativa(() ->
                restClient.post().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(corpo)
                        .retrieve()
                        .body(EmbeddingResposta.class));

        if (resposta == null || resposta.data() == null || resposta.data().size() != textos.size()) {
            throw new AiProviderException("Resposta de embeddings com tamanho inesperado.");
        }
        return resposta.data().stream()
                .map(datum -> validarDimensao(datum.embedding()))
                .toList();
    }

    // ------------------------------------------------------------------ //
    // Chat                                                                //
    // ------------------------------------------------------------------ //

    @Override
    public ChatResult gerar(List<ChatMessage> mensagens) {
        garantirChatConfigurado();
        String url = joinearUrl("/chat/completions");
        String autorizacao = comAutorizacao();
        var corpo = Map.of(
                "model", properties.chatModel(),
                "messages", mensagens,
                "max_tokens", properties.maxOutputTokens());

        ChatResposta resposta = comRetentativa(() ->
                restClient.post().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, autorizacao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(corpo)
                        .retrieve()
                        .body(ChatResposta.class));

        if (resposta == null || resposta.choices() == null || resposta.choices().isEmpty()) {
            throw new AiProviderException("Resposta de chat vazia.");
        }
        ChatChoice escolha = resposta.choices().get(0);
        String content = escolha == null || escolha.message() == null ? null : escolha.message().content();
        if (content == null) {
            throw new AiProviderException("Resposta de chat sem conteudo.");
        }
        Integer promptTokens = resposta.usage() == null ? null : resposta.usage().prompt_tokens();
        Integer completionTokens = resposta.usage() == null ? null : resposta.usage().completion_tokens();
        return new ChatResult(content, promptTokens, completionTokens);
    }

    // ------------------------------------------------------------------ //
    // Disponibilidade                                                     //
    // ------------------------------------------------------------------ //

    @Override
    public boolean chatDisponivel() {
        return properties.isConfigured();
    }

    @Override
    public boolean embeddingsDisponiveis() {
        return properties.embeddingsConfigured();
    }

    @Override
    public String modelo() {
        return properties.chatModel();
    }

    @Override
    public int dimensaoConfigurada() {
        return properties.embeddingDimensions();
    }

    // ------------------------------------------------------------------ //
    // Internos                                                            //
    // ------------------------------------------------------------------ //

    private void garantirEmbeddingsConfigurados() {
        if (!properties.embeddingsConfigured()) {
            throw new AiNotConfiguredException();
        }
    }

    private void garantirChatConfigurado() {
        if (!properties.isConfigured()) {
            throw new AiNotConfiguredException();
        }
    }

    /** Header Authorization apenas quando uma chave veio do ambiente (Ollama local nao exige). */
    private String comAutorizacao() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return "";
        }
        return "Bearer " + properties.apiKey().trim();
    }

    private String joinearUrl(String caminho) {
        String base = properties.baseUrl() == null ? "" : properties.baseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + caminho;
    }

    private float[] validarDimensao(float[] vetor) {
        int esperado = properties.embeddingDimensions();
        if (esperado > 0 && (vetor == null || vetor.length != esperado)) {
            throw new AiProviderException(
                    "O provedor retornou vetores com dimensao diferente da configurada (" + esperado + ").");
        }
        return vetor;
    }

    /**
     * Executa a chamada com politicas de reetentava (429/5xx/tempo) e
     * quando a falha e definitiva (401/403/malformacao) propaga de imediato.
     */
    private <T> T comRetentativa(java.util.function.Supplier<T> chamada) {
        int tentativas = Math.max(0, properties.maxRetries()) + 1;
        RuntimeException ultima = null;
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            try {
                return chamada.get();
            } catch (AiProviderException permanente) {
                throw permanente;
            } catch (org.springframework.web.client.HttpClientErrorException.Conflict erro) {
                // 409 nunca e transitorio: propagar sem reetentar
                throw new AiProviderException(erro.getMessage());
            } catch (org.springframework.web.client.HttpClientErrorException.Forbidden | org.springframework.web.client.HttpClientErrorException.Unauthorized erro) {
                LOG.warn("Provedor de IA rejeitou credenciais ({}).", erro.getStatusCode());
                throw new AiProviderException("Chave invalida ou sem authorize.");
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests erro) {
                ultima = erro;
                LOG.info("Limite de requisicoes do provedor (429). Tentativa {}/{}.", tentativa, tentativas);
            } catch (org.springframework.web.client.ResourceAccessException | org.springframework.web.client.HttpServerErrorException erro) {
                ultima = erro;
                LOG.info("Falha temporaria do provedor de IA ({}). Tentativa {}/{}.",
                        erro.getClass().getSimpleName(), tentativa, tentativas);
            }
            if (tentativa < tentativas) {
                dormir(Math.min(2000L, 250L * tentativa));
            }
        }
        throw new AiProviderException(ultima == null ? null : ultima.getClass().getSimpleName());
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Aguardando reetentativa interrompida.");
        }
    }

    // CONTRATOS JSON -----------------------------------------------------

    public record EmbeddingResposta(List<EmbeddingDatum> data) {
    }

    public record EmbeddingDatum(float[] embedding) {
    }

    public record ChatResposta(List<ChatChoice> choices, ChatUsage usage) {
    }

    public record ChatChoice(ChatRespostaMensagem message) {
    }

    public record ChatRespostaMensagem(String content) {
    }

    public record ChatUsage(Integer prompt_tokens, Integer completion_tokens) {
    }
}
