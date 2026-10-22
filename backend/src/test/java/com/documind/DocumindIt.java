package com.documind;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integracao contra PostgreSQL REAL com pgvector.
 *
 * Dependem de infraestrutura externa; por isso so executam quando
 * DOCUMIND_IT=true (controle de custo/velocidade no CI):
 *
 *   powershell: $env:DOCUMIND_IT="true"; .\mvnw.cmd test
 *
 * Sem a variavel, sao PULADOS e o desenvolvimento roda apenas unitarios.
 */
@SpringBootTest(properties = {
        // Segredo JWT forte apenas para o contexto de testes (nao e commitado).
        "DOCUMIND_JWT_SECRET=segredo-integracao-local-32-chars-minimo!!",
        "spring.task.scheduling.pool.size=2"
})
@EnabledIfEnvironmentVariable(named = "DOCUMIND_IT", matches = "true")
class DocumindIt {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void extensaoPgvectorInstalada() {
        // Extension pgvector presente (pre-requisito do schema)
        String extensao = jdbcTemplate.queryForObject(
                "select extname from pg_extension where extname = 'vector'", String.class);
        assertThat(extensao).isEqualTo("vector");
    }

    @Test
    void tabelasDoSchemaExistem() {
        Integer total = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('usuario','documento','trecho_documento','colecao',
                                     'documento_colecao','tarefa_processamento','conversa',
                                     'mensagem','citacao')
                """, Integer.class);
        assertThat(total).isEqualTo(9);
    }

    @Test
    void colunaVetorTemDimensao768() {
        String tipo = jdbcTemplate.queryForObject("""
                select format_type(a.atttypid, a.atttypmod)
                from pg_attribute a
                join pg_class c on c.oid = a.attrelid
                where c.relname = 'trecho_documento' and a.attname = 'embedding'
                """, String.class);
        assertThat(tipo).isEqualTo("vector(768)");
    }
}
