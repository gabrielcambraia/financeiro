package com.financeiro.entity;

import com.financeiro.entity.enums.TipoPessoa;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "filiais")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Filial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", nullable = false)
    private TipoPessoa tipoPessoa;

    @Column(nullable = false)
    private String nome;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "documento_cifrado", nullable = false)
    private byte[] documentoCifrado;

    @Column(name = "documento_hash", nullable = false)
    private String documentoHash;

    @Column(name = "inscricao_estadual")
    private String inscricaoEstadual;

    @Column(name = "data_nascimento")
    private String dataNascimento;

    private String email;

    private String telefone;

    private String cep;

    private String logradouro;

    private String numero;

    private String complemento;

    private String bairro;

    private String cidade;

    private String uf;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (this.criadoEm == null) this.criadoEm = agora;
        if (this.atualizadoEm == null) this.atualizadoEm = agora;
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
