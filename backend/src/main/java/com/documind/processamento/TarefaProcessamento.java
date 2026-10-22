package com.documind.processamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tarefa de processamento (fila persistida). Um documento tem uma tarefa
 * vigente por vez; a fila em si e entregue por executor em memoria, com
 * recovery de tarefas pendentes pelo scheduler.
 */
@Entity
@Table(name = "tarefa_processamento")
public class TarefaProcessamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTarefa estado = EstadoTarefa.PENDENTE;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    @Column(name = "data_criacao", nullable = false, updatable = false)
    private Instant dataCriacao;

    @Column(name = "data_inicio")
    private Instant dataInicio;

    @Column(name = "data_conclusao")
    private Instant dataConclusao;

    @Column(name = "data_atualizacao", nullable = false)
    private Instant dataAtualizacao;

    public static TarefaProcessamento nova(Long documentoId) {
        TarefaProcessamento tarefa = new TarefaProcessamento();
        tarefa.documentoId = documentoId;
        return tarefa;
    }

    public void marcarEmExecucao() {
        estado = EstadoTarefa.EM_EXECUCAO;
        dataInicio = Instant.now();
        tentativas++;
    }

    public void marcarConcluida() {
        estado = EstadoTarefa.CONCLUIDA;
        dataConclusao = Instant.now();
        ultimoErro = null;
    }

    public void marcarFalha(String erroResumido) {
        estado = EstadoTarefa.FALHA;
        ultimoErro = erroResumido;
        dataConclusao = Instant.now();
    }

    public void reenfileirar() {
        estado = EstadoTarefa.PENDENTE;
        ultimoErro = null;
        dataConclusao = null;
    }

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

    public Long getId() {
        return id;
    }

    public Long getDocumentoId() {
        return documentoId;
    }

    public EstadoTarefa getEstado() {
        return estado;
    }

    public void setEstado(EstadoTarefa estado) {
        this.estado = estado;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }

    public void setUltimoErro(String ultimoErro) {
        this.ultimoErro = ultimoErro;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }

    public Instant getDataInicio() {
        return dataInicio;
    }

    public Instant getDataConclusao() {
        return dataConclusao;
    }
}
