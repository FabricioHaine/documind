package com.documind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propriedades gerais da aplicacao (prefixo `app`), todas sobrescriviveis por
 * variaveis de ambiente (ver application.yml).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        Storage storage,
        Documents documents,
        Rag rag,
        Security security
) {

    public record Cors(List<String> allowedOrigins) {
        public String[] asArray() {
            return allowedOrigins == null ? new String[0] : allowedOrigins.toArray(new String[0]);
        }
    }

    public record Storage(String path) {
    }

    public record Documents(int maxSizeMb, int uploadsPerHour) {
    }

    public record Rag(int chunkSizeChars, int chunkOverlapChars) {
    }

    public record Security(int jwtExpMinutes, int rateLimitPerMinute) {
    }
}
