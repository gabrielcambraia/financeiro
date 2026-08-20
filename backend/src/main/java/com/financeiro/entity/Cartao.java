package com.financeiro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "cartoes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private BigDecimal limite;

    @Column(name = "dia_fechamento", nullable = false)
    private Integer diaFechamento;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conta_pagamento_id", nullable = false)
    private Conta contaPagamento;

    @Column(nullable = false)
    private String cor;

    @Column(nullable = false)
    private String icone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "banco_id")
    private Banco banco;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;
}
