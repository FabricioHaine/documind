package com.documind.health;

import com.documind.config.AiProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Saude do sistema para operacao/integracao:
 * - banco: testado com consulta real (SELECT 1);
 * - ia: status de configuracao SEMVALIDAR conectividade (evita custo/latencia
 *   no poll de health). Os modelos exibidos sao os configurados via ambiente.
 *
 * Responde sempre 200 com o estado por componente — status "DOWN" nao deve
 * esconder o diagnostico de quem consulta.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Saúde")
public class HealthController {

    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;
    private final AiProperties aiProperties;

    public HealthController(JdbcTemplate jdbcTemplate, AiProperties aiProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiProperties = aiProperties;
    }

    @GetMapping
    @Operation(summary = "Saude do sistema (banco de dados e configuracao de IA)")
    public Map<String, Object> saude() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("banco", verificarBanco());
        resposta.put("ia", estadoIa());
        resposta.put("timestamp", Instant.now().toString());
        return resposta;
    }

    private Map<String, Object> verificarBanco() {
        Map<String, Object> banco = new LinkedHashMap<>();
        try {
            Integer resultado = jdbcTemplate.queryForObject("select 1", Integer.class);
            boolean ok = resultado != null && resultado == 1;
            banco.put("estado", ok ? "UP" : "DOWN");
            if (!ok) {
                banco.put("detalhe", "Consulta de saude retornou valor inesperado.");
            }
        } catch (Exception e) {
            LOG.warn("Health check do banco falhou: {}", e.getMessage());
            banco.put("estado", "DOWN");
            banco.put("detalhe", "Banco de dados inacessivel.");
        }
        return banco;
    }

    private Map<String, Object> estadoIa() {
        Map<String, Object> ia = new LinkedHashMap<>();
        ia.put("configurado", aiProperties.isConfigured());
        ia.put("embeddingsConfigurado", aiProperties.embeddingsConfigured());
        if (!"https://api.openai.com/v1".equals(aiProperties.baseUrl())
                && aiProperties.baseUrl() != null && !aiProperties.baseUrl().isBlank()) {
            String base = aiProperties.baseUrl();
            // Nunca expor cabecalhos/chaves — apenas o host base, util para diagnostico.
            ia.put("baseUrl", base);
        }
        if (texto(aiProperties.chatModel())) {
            ia.put("modeloChat", aiProperties.chatModel());
        }
        if (texto(aiProperties.embeddingModel())) {
            ia.put("modeloEmbeddings", aiProperties.embeddingModel());
            ia.put("dimensoesEmbedding", aiProperties.embeddingDimensions());
        }
        return ia;
    }

    private boolean texto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
