package com.documind.documento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrechoDocumentoRepository extends JpaRepository<TrechoDocumento, Long> {

    long countByDocumentoId(Long documentoId);

    void deleteByDocumentoId(Long documentoId);
}
