package com.documind.colecao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/**
 * Associacao N:N entre documentos e colecoes.
 * Regra: um documento pode pertencer a varias colecoes.
 */
@Entity
@Table(name = "documento_colecao")
@IdClass(DocumentoColecao.DocumentoColecaoId.class)
public class DocumentoColecao {

    @Id
    @Column(name = "colecao_id")
    private Long colecaoId;

    @Id
    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "data_inclusao", nullable = false)
    private Instant dataInclusao;

    public static DocumentoColecao de(Long colecaoId, Long documentoId) {
        DocumentoColecao vinculo = new DocumentoColecao();
        vinculo.colecaoId = colecaoId;
        vinculo.documentoId = documentoId;
        vinculo.dataInclusao = Instant.now();
        return vinculo;
    }

    public Long getColecaoId() {
        return colecaoId;
    }

    public Long getDocumentoId() {
        return documentoId;
    }

    public Instant getDataInclusao() {
        return dataInclusao;
    }

    public record DocumentoColecaoId(Long colecaoId, Long documentoId) implements Serializable {
    }
}
