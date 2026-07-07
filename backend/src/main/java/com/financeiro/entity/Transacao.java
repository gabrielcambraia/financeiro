package com.financeiro.entity;

import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "transacoes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false)
    private TipoPagamento tipoPagamento;

    @Column(nullable = false)
    private BigDecimal valor;

    private String descricao;

    @Column(nullable = false)
    private String data;

    @Column(name = "fixa", nullable = false)
    private boolean fixa;

    @Builder.Default
    @Column(name = "saldo_ajustado", nullable = false)
    private boolean saldoAjustado = true;

    @Column(name = "total_parcelas")
    private Integer totalParcelas;

    @Column(name = "numero_parcela")
    private Integer numeroParcela;

    @Column(name = "grupo_parcela_id")
    private String grupoParcelaId;

    @Column(name = "criado_em", nullable = false)
    private String criadoEm;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
