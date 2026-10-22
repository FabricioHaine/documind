package com.documind.colecao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentoColecaoRepository extends JpaRepository<DocumentoColecao, DocumentoColecao.DocumentoColecaoId> {

    boolean existsByColecaoIdAndDocumentoId(Long colecaoId, Long documentoId);

    boolean existsByColecaoId(Long colecaoId);

    void deleteByColecaoIdAndDocumentoId(Long colecaoId, Long documentoId);

    List<DocumentoColecao> findByDocumentoId(Long documentoId);

    List<DocumentoColecao> findByColecaoId(Long colecaoId);

    List<DocumentoColecao> findByDocumentoIdIn(java.util.Collection<Long> documentoIds);

    long countByColecaoId(Long colecaoId);
}
