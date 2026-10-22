package com.documind.colecao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ColecaoRepository extends JpaRepository<Colecao, Long> {

    Optional<Colecao> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Colecao> findByUsuarioIdOrderByDataAtualizacaoDesc(Long usuarioId);

    @Query("select (count(c) > 0) from Colecao c where c.usuario.id = :usuarioId " +
            "and lower(c.nome) = lower(:nome)")
    boolean existsByUsuarioIdAndNome(@Param("usuarioId") Long usuarioId, @Param("nome") String nome);

    @Query("select (count(c) > 0) from Colecao c where c.usuario.id = :usuarioId " +
            "and lower(c.nome) = lower(:nome) and c.id <> :idExcluido")
    boolean existsByUsuarioIdAndNomeExcluding(@Param("usuarioId") Long usuarioId,
                                              @Param("nome") String nome,
                                              @Param("idExcluido") Long idExcluido);
}
