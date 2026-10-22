package com.documind.conversa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversaRepository extends JpaRepository<Conversa, Long> {

    Optional<Conversa> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Conversa> findByUsuarioIdOrderByDataAtualizacaoDesc(Long usuarioId);
}
