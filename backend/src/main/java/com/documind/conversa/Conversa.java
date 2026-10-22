package com.documind.conversa;

import com.documind.colecao.Colecao;
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

/**
 * Conversa de chat. O escopo documental (colecao) define ONDE o RAG pesa:
 * colecao null = todos os documentos ativos do usuario (sempre respeitando
 * isolamento de propriedade no SQL de busca vetorial).
 */
@Entity
@Table(name = "conversa")
public class Conversa {

    public static final String TITULO_PADRAO = "Nova conversa";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "colecao_id")
    private Colecao colecao;

    @Column(nullable = false, length = 160)
    private String titulo = TITULO_PADRAO;

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

    /** Atualiza o carimbo de atividade (nova mensagem). */
    public void tocar() {
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

    public Colecao getColecao() {
        return colecao;
    }

    public void setColecao(Colecao colecao) {
        this.colecao = colecao;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public Instant getDataAtualizacao() {
        return dataAtualizacao;
    }
}
