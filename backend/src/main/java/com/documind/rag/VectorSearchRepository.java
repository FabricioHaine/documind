package com.documind.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Operacoes vetoriais com pgvector via JDBC nativo.
 *
 * Por que aqui e nao na entidade JPA: o tipo `vector` e especifico do
 * PostgreSQL/extension. Escritas e buscas usam `CAST(? AS vector)` com
 * literais de texto — SQL explicito, previsivel e testavel com banco real.
 *
 * ISOLAMENTO: TODA consulta restringe documentos pelo usuario logado; nunca
 * e possível recuperar trecho de outro usuario, mesmo semelhante.
 */
@Repository
public class VectorSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insere (ou atualiza por (documento, indice)) o trecho com embedding.
     * Idempotente: reprocessar o documento NAO duplica trechos.
     */
    public void inserir(long documentoId, int indiceTrecho, String conteudo,
                        Integer paginaInicio, Integer paginaFim,
                        String metadadosJson, String modeloEmbedding, String vetorLiteral) {
        jdbcTemplate.update("""
                        INSERT INTO trecho_documento
                            (documento_id, indice_trecho, conteudo, pagina_inicio, pagina_fim,
                             metadados, modelo_embedding, embedding)
                        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, CAST(? AS vector))
                        ON CONFLICT (documento_id, indice_trecho) DO UPDATE SET
                            conteudo = EXCLUDED.conteudo,
                            pagina_inicio = EXCLUDED.pagina_inicio,
                            pagina_fim = EXCLUDED.pagina_fim,
                            metadados = EXCLUDED.metadados,
                            modelo_embedding = EXCLUDED.modelo_embedding,
                            embedding = EXCLUDED.embedding
                        """,
                documentoId, indiceTrecho, conteudo, paginaInicio, paginaFim,
                metadadosJson, modeloEmbedding, vetorLiteral);
    }

    /**
     * Busca vetorial por similaridade de cosseno restringida ao usuario
     * (e, opcionalmente, a uma colecao) e ao modelo corrente de embeddings.
     * Retorna os K mais proximos ja filtrados pelo limiar de relevancia.
     */
    public List<TrechoRanqueado> buscaPorSimilaridade(long usuarioId, Long colecaoId,
                                                     String modeloEmbedding,
                                                     String vetorLiteralDaPergunta,
                                                     int limite, double similaridadeMinima) {
        String sql = """
                SELECT t.id                  AS trecho_id,
                       t.documento_id        AS documento_id,
                       t.indice_trecho       AS indice_trecho,
                       t.conteudo            AS conteudo,
                       t.pagina_inicio       AS pagina_inicio,
                       t.pagina_fim          AS pagina_fim,
                       d.nome_original       AS documento_nome,
                       d.tipo_arquivo        AS documento_tipo,
                       (1 - (t.embedding <=> CAST(? AS vector))) AS similaridade
                FROM trecho_documento t
                JOIN documento d ON d.id = t.documento_id
                WHERE t.modelo_embedding = ?
                  AND d.usuario_id = ?
                  AND d.estado = 'CONCLUIDO'
                  AND (CAST(? AS bigint) IS NULL OR EXISTS (
                        SELECT 1 FROM documento_colecao dc
                        WHERE dc.documento_id = d.id AND dc.colecao_id = ?))
                  AND (1 - (t.embedding <=> CAST(? AS vector))) >= ?
                ORDER BY t.embedding <=> CAST(? AS vector)
                LIMIT ?
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNumber) -> new TrechoRanqueado(
                        rs.getLong("trecho_id"),
                        rs.getLong("documento_id"),
                        rs.getInt("indice_trecho"),
                        rs.getString("conteudo"),
                        (Integer) rs.getObject("pagina_inicio"),
                        (Integer) rs.getObject("pagina_fim"),
                        rs.getString("documento_nome"),
                        rs.getString("documento_tipo"),
                        rs.getDouble("similaridade")),
                vetorLiteralDaPergunta, modeloEmbedding, usuarioId,
                colecaoId, colecaoId,
                vetorLiteralDaPergunta, similaridadeMinima,
                vetorLiteralDaPergunta, limite);
    }

    /** Quantidade de trechos indexados de um documento. */
    public long contarTrechos(long documentoId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM trecho_documento WHERE documento_id = ?",
                Long.class, documentoId);
        return count == null ? 0 : count;
    }

    /** Exclusao explicita dos trechos (reprocesso). */
    public void excluirTrechosDoDocumento(long documentoId) {
        jdbcTemplate.update("DELETE FROM trecho_documento WHERE documento_id = ?", documentoId);
    }

    /** Existe o documento (e nao excluido enquanto processava)? */
    public boolean existeDocumento(long documentoId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documento WHERE id = ?", Integer.class, documentoId);
        return n != null && n > 0;
    }

    /**
     * Resultado de uma busca vetorial. `trechoId` + `indiceTrecho` sao as raizes
     * reais no banco: as citacoes exibidas no chat derivam deles (o modelo
     * nunca decide identificadores).
     */
    public record TrechoRanqueado(
            long trechoId,
            long documentoId,
            int indiceTrecho,
            String conteudo,
            Integer paginaInicio,
            Integer paginaFim,
            String documentoNome,
            String documentoTipo,
            double similaridade) {
    }
}
