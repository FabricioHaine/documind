package com.documind.conversa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Mensagem da conversa. Uma resposta SEM estado OK significa exatamente o
 * que diz: SEM_EVIDENCIA (retrieval vazio, decidido pelo backend) ou ERRO
 * (falha de geracao registrada de forma consistente no historico).
 */
@Entity
@Table(name = "mensagem")
public class Mensagem {

    public enum AutorMensagem {
        USUARIO, ASSISTENTE, SISTEMA
    }

    public enum EstadoMensagem {
        OK, SEM_EVIDENCIA, ERRO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversa_id", nullable = false)
    private Long conversaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AutorMensagem autor;

    @Column(nullable = false, columnDefinition = "text")
    private String conteudo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMensagem estado;

    /** JSON com metadados tecnicos seguros (modelo, tokens, latencia). */
    @Column(nullable = false, columnDefinition = "text")
    private String metadados = "{}";

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    public static Mensagem perguntaUsuario(Long conversaId, String conteudo) {
        Mensagem mensagem = new Mensagem();
        mensagem.conversaId = conversaId;
        mensagem.autor = AutorMensagem.USUARIO;
        mensagem.conteudo = conteudo;
        mensagem.estado = EstadoMensagem.OK;
        mensagem.metadados = "{}";
        return mensagem;
    }

    public static Mensagem respostaAssistente(Long conversaId, String conteudo, EstadoMensagem estado,
                                              String metadadosJson) {
        Mensagem mensagem = new Mensagem();
        mensagem.conversaId = conversaId;
        mensagem.autor = AutorMensagem.ASSISTENTE;
        mensagem.conteudo = conteudo;
        mensagem.estado = estado;
        mensagem.metadados = metadadosJson == null ? "{}" : metadadosJson;
        return mensagem;
    }

    @PrePersist
    void aoCriar() {
        if (dataCriacao == null) {
            dataCriacao = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getConversaId() {
        return conversaId;
    }

    public void setConversaId(Long conversaId) {
        this.conversaId = conversaId;
    }

    public AutorMensagem getAutor() {
        return autor;
    }

    public void setAutor(AutorMensagem autor) {
        this.autor = autor;
    }

    public String getConteudo() {
        return conteudo;
    }

    public void setConteudo(String conteudo) {
        this.conteudo = conteudo;
    }

    public EstadoMensagem getEstado() {
        return estado;
    }

    public void setEstado(EstadoMensagem estado) {
        this.estado = estado;
    }

    public String getMetadados() {
        return metadados;
    }

    public void setMetadados(String metadados) {
        this.metadados = metadados;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }
}
