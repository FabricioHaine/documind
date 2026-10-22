package com.documind.conversa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    List<Mensagem> findByConversaIdOrderByDataCriacaoAsc(Long conversaId);
}
