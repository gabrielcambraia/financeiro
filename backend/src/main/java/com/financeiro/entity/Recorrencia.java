package com.financeiro.entity;

import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recorrencias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false)
    private TipoPagamento tipoPagamento;

    @Column(name = "conta_id")
    private Long contaId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conta_id", insertable = false, updatable = false)
    private Conta conta;

    @Column(name = "cartao_id")
    private Long cartaoId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cartao_id", insertable = false, updatable = false)
    private Cartao cartao;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", insertable = false, updatable = false)
    private Categoria categoria;

    @Column(name = "centro_custo_id")
    private Long centroCustoId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "centro_custo_id", insertable = false, updatable = false)
    private CentroCusto centroCusto;

    @Column(nullable = false)
    private BigDecimal valor;

    private String descricao;

    @Column(name = "dia_competencia", nullable = false)
    private int diaCompetencia;

    @Column(name = "dia_vencimento")
    private Integer diaVencimento;

    @Builder.Default
    @Column(name = "debito_automatico", nullable = false)
    private boolean debitoAutomatico = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) this.criadoEm = LocalDateTime.now();
        if (this.atualizadoEm == null) this.atualizadoEm = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
