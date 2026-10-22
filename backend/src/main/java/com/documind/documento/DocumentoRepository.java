package com.documind.documento;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long>, JpaSpecificationExecutor<Documento> {

    @EntityGraph(attributePaths = "usuario")
    Optional<Documento> findWithUsuarioById(Long id);

    interface Specs {
        static Specification<Documento> doUsuario(Long usuarioId) {
            return (root, query, cb) -> cb.equal(root.get("usuario").get("id"), usuarioId);
        }

        static Specification<Documento> nomeContem(String nome) {
            return (root, query, cb) -> cb.like(cb.lower(root.get("nomeOriginal")),
                    "%" + nome.toLowerCase() + "%");
        }

        static Specification<Documento> tipoIgual(String tipo) {
            return (root, query, cb) -> cb.equal(cb.upper(root.get("tipoArquivo")), tipo.toUpperCase());
        }

        static Specification<Documento> estadoIgual(EstadoProcessamento estado) {
            return (root, query, cb) -> cb.equal(root.get("estado"), estado);
        }

        static Specification<Documento> colecao(Long colecaoId) {
            return (root, query, cb) -> {
                var sub = query.subquery(Long.class);
                var dc = sub.from(com.documind.colecao.DocumentoColecao.class);
                sub.select(dc.get("id").get("documentoId"))
                        .where(cb.equal(dc.get("id").get("colecaoId"), colecaoId));
                return root.get("id").in(sub);
            };
        }

        static Specification<Documento> enviadoEm(java.time.LocalDate data) {
            return (root, query, cb) -> cb.equal(cb.function("date", java.time.LocalDate.class, root.get("dataUpload")), data);
        }
    }
}
