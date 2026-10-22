package com.documind.conversa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Citacao de fonte de uma resposta. O backend constrói cada registro a partir
 * dos trechos REALMENTE recuperados na busca vetorial — os campos guardam
 * snapshot (conteudo do trecho, paginas) para verificacao no front-end.
 * O modelo de IA nunca define identificadores de fonte.
 */
@Entity
@Table(name = "citacao")
public class Citacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensagem_id", nullable = false)
    private Long mensagemId;

    @Column(name = "documento_id")
    private Long documentoId;

    @Column(name = "trecho_documento_id")
    private Long trechoDocumentoId;

    /** Numero da referencia usado no prompt ([1], [2]...) — mesma ordem da recuperacao. */
    @Column(nullable = false)
    private int referencia;

    @Column(name = "indice_trecho", nullable = false)
    private int indiceTrecho;

    @Column(name = "pagina_inicio")
    private Integer paginaInicio;

    @Column(name = "pagina_fim")
    private Integer paginaFim;

    @Column(name = "conteudo_trecho", nullable = false, columnDefinition = "text")
    private String conteudoTrecho;

    public Long getId() {
        return id;
    }

    public Long getMensagemId() {
        return mensagemId;
    }

    public void setMensagemId(Long mensagemId) {
        this.mensagemId = mensagemId;
    }

    public Long getDocumentoId() {
        return documentoId;
    }

    public void setDocumentoId(Long documentoId) {
        this.documentoId = documentoId;
    }

    public Long getTrechoDocumentoId() {
        return trechoDocumentoId;
    }

    public void setTrechoDocumentoId(Long trechoDocumentoId) {
        this.trechoDocumentoId = trechoDocumentoId;
    }

    public int getReferencia() {
        return referencia;
    }

    public void setReferencia(int referencia) {
        this.referencia = referencia;
    }

    public int getIndiceTrecho() {
        return indiceTrecho;
    }

    public void setIndiceTrecho(int indiceTrecho) {
        this.indiceTrecho = indiceTrecho;
    }

    public Integer getPaginaInicio() {
        return paginaInicio;
    }

    public void setPaginaInicio(Integer paginaInicio) {
        this.paginaInicio = paginaInicio;
    }

    public Integer getPaginaFim() {
        return paginaFim;
    }

    public void setPaginaFim(Integer paginaFim) {
        this.paginaFim = paginaFim;
    }

    public String getConteudoTrecho() {
        return conteudoTrecho;
    }

    public void setConteudoTrecho(String conteudoTrecho) {
        this.conteudoTrecho = conteudoTrecho;
    }
}
