package com.documind.colecao;

import com.documind.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "colecao")
public class Colecao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private Instant dataAtualizacao;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        dataCriacao = agora;
        dataAtualizacao = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        dataAtualizacao = Instant.now();
    }

    public boolean pertenceA(Long usuarioId) {
        return usuario != null && usuarioId != null && usuarioId.equals(usuario.getId());
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public Instant getDataAtualizacao() {
        return dataAtualizacao;
    }
}
