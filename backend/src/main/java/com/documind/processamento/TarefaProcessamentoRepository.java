package com.documind.processamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TarefaProcessamentoRepository extends JpaRepository<TarefaProcessamento, Long> {

    Optional<TarefaProcessamento> findFirstByDocumentoIdOrderByDataCriacaoDesc(Long documentoId);

    List<TarefaProcessamento> findByEstado(EstadoTarefa estado);

    void deleteByDocumentoId(Long documentoId);
}
