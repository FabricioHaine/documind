package com.documind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Beans gerais da aplicacao: habilita as propriedades tipadas e o cliente HTTP
 * para integracao com o provedor de IA (com timeouts configuraveis).
 */
@Configuration
@EnableConfigurationProperties({AppProperties.class, AiProperties.class})
public class AppConfig {

    /**
     * Cliente HTTP usado por todos os endpoints do provedor de IA (API
     * compativel com OpenAI). A URL base e definida em tempo de chamada pelo
     * provedor, permitindo trocar provedor/modelo sem reescrever codigo.
     */
    @Bean
    public RestClient aiRestClient(AiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds()));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
