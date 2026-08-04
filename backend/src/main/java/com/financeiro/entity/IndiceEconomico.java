package com.financeiro.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Cache local de séries mensais do SGS/Banco Central (CDI, Selic, IPCA).
 * Dado público, sem {@code espacoId} — mesma natureza do catálogo de bancos
 * ({@code Banco}). Alimentada por {@code ServicoIndiceEconomico} e consumida
 * por {@code AgendadorRendimento}.
 */
@Entity
@Table(name = "indices_economicos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndiceEconomico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código da série no SGS (ex.: 4391 = CDI, 4390 = Selic, 433 = IPCA). */
    @Column(nullable = false)
    private String codigo;

    /** Mês de referência, formato {@code yyyy-MM}. */
    @Column(nullable = false)
    private String mes;

    /** Percentual do mês (ex.: 0.85 = 0,85%), como publicado pelo SGS. */
    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal valor;
}
