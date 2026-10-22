package com.documind.conversa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CitacaoRepository extends JpaRepository<Citacao, Long> {

    List<Citacao> findByMensagemIdOrderByReferenciaAsc(Long mensagemId);

    List<Citacao> findByMensagemIdIn(Collection<Long> mensagemIds);
}
