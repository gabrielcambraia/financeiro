package com.financeiro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Limite de gasto mensal por categoria. {@code mes} fica no formato
 * "yyyy-MM" (mesmo padrão usado nos filtros de transação por mês).
 */
@Entity
@Table(name = "orcamentos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orcamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false)
    private String mes;

    @Column(nullable = false)
    private BigDecimal limite;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;
}
