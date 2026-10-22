package com.documind.documento;

import com.documind.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "documento")
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Nome exibido ao usuario; NUNCA usado como caminho fisico. */
    @Column(name = "nome_original", nullable = false, length = 260)
    private String nomeOriginal;

    /** Identificador interno gerado pela aplicacao (UUID + extensao). */
    @Column(name = "nome_armazenamento", nullable = false, unique = true, length = 64)
    private String nomeArmazenamento;

    @Column(name = "tipo_arquivo", nullable = false, length = 10)
    private String tipoArquivo;

    @Column(name = "tipo_mime", nullable = false, length = 120)
    private String tipoMime;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(name = "total_paginas")
    private Integer totalPaginas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EstadoProcessamento estado = EstadoProcessamento.AGUARDANDO_PROCESSAMENTO;

    /** Mensagem segura (sem stack trace / detalhes internos). */
    @Column(name = "mensagem_erro", length = 500)
    private String mensagemErro;

    @Column(name = "modelo_embedding", length = 80)
    private String modeloEmbedding;

    @Column(name = "data_upload", nullable = false)
    private Instant dataUpload;

    @Column(name = "data_processamento")
    private Instant dataProcessamento;

    @PrePersist
    void aoCriar() {
        if (dataUpload == null) {
            dataUpload = Instant.now();
        }
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

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public void setNomeOriginal(String nomeOriginal) {
        this.nomeOriginal = nomeOriginal;
    }

    public String getNomeArmazenamento() {
        return nomeArmazenamento;
    }

    public void setNomeArmazenamento(String nomeArmazenamento) {
        this.nomeArmazenamento = nomeArmazenamento;
    }

    public String getTipoArquivo() {
        return tipoArquivo;
    }

    public void setTipoArquivo(String tipoArquivo) {
        this.tipoArquivo = tipoArquivo;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public void setTipoMime(String tipoMime) {
        this.tipoMime = tipoMime;
    }

    public long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public Integer getTotalPaginas() {
        return totalPaginas;
    }

    public void setTotalPaginas(Integer totalPaginas) {
        this.totalPaginas = totalPaginas;
    }

    public EstadoProcessamento getEstado() {
        return estado;
    }

    public void setEstado(EstadoProcessamento estado) {
        this.estado = estado;
    }

    public String getMensagemErro() {
        return mensagemErro;
    }

    public void setMensagemErro(String mensagemErro) {
        this.mensagemErro = mensagemErro;
    }

    public String getModeloEmbedding() {
        return modeloEmbedding;
    }

    public void setModeloEmbedding(String modeloEmbedding) {
        this.modeloEmbedding = modeloEmbedding;
    }

    public Instant getDataUpload() {
        return dataUpload;
    }

    public void setDataUpload(Instant dataUpload) {
        this.dataUpload = dataUpload;
    }

    public Instant getDataProcessamento() {
        return dataProcessamento;
    }

    public void setDataProcessamento(Instant dataProcessamento) {
        this.dataProcessamento = dataProcessamento;
    }
}
