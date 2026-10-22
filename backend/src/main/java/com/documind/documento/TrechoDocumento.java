package com.documind.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Trecho de texto indexado. O embedding e o METADADOS JSONB NAO sao
 * geridos por esta entidade: escrita/binaria e busca vetorial ocorrem via
 * SQL nativo com casting explicito (VectorSearchRepository), evitando
 * incompatibilidades de mapeamento e garantindo SQL testavel com banco real.
 */
@Entity
@Table(name = "trecho_documento")
public class TrechoDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @Column(name = "indice_trecho", nullable = false)
    private int indiceTrecho;

    @Column(nullable = false, columnDefinition = "text")
    private String conteudo;

    @Column(name = "pagina_inicio")
    private Integer paginaInicio;

    @Column(name = "pagina_fim")
    private Integer paginaFim;

    /** Espelha o JSONB (escrito via SQL nativo com CAST); apenas leitura. */
    @Column(name = "metadados", columnDefinition = "text")
    private String metadados;

    @Column(name = "modelo_embedding", nullable = false, length = 80)
    private String modeloEmbedding;

    public Long getId() {
        return id;
    }

    public Long getDocumentoId() {
        return documentoId;
    }

    public int getIndiceTrecho() {
        return indiceTrecho;
    }

    public String getConteudo() {
        return conteudo;
    }

    public Integer getPaginaInicio() {
        return paginaInicio;
    }

    public Integer getPaginaFim() {
        return paginaFim;
    }

    public String getMetadados() {
        return metadados;
    }

    public String getModeloEmbedding() {
        return modeloEmbedding;
    }
}
