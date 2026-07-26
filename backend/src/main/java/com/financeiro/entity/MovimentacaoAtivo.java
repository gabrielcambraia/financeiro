package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDate;
import com.financeiro.entity.enums.TipoMovimentacaoAtivo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Histórico único do ativo (aporte, resgate e rendimento). Aporte/resgate
 * também geram uma {@link Transacao} real (dinheiro saindo/voltando de uma
 * conta) — {@code transacao} fica nulo para rendimento, que é ganho contábil
 * do próprio ativo, sem mexer em saldo de conta nenhuma.
 */
@Entity
@Table(name = "movimentacoes_ativo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoAtivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ativo_id", nullable = false)
    private Ativo ativo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacaoAtivo tipo;

    @Column(nullable = false)
    private BigDecimal valor;

    @Convert(converter = ConversorLocalDate.class)
    @Column(nullable = false)
    private LocalDate data;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transacao_id")
    private Transacao transacao;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;
}
