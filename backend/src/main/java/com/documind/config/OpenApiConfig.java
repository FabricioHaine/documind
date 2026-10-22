package com.documind.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI com esquema Bearer para testar endpoints protegidos.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("DocuMind API")
                        .version("0.1.0")
                        .description("API da plataforma de inteligencia documental com RAG. "
                                + "Autenticacao via token JWT (header Authorization: Bearer <token>)."))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
