package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDate;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Gerada só no momento do fechamento (ver AgendadorFatura): agrupa os
 * {@link ItemFatura} soltos daquele ciclo e aponta para a despesa (débito)
 * criada na conta de pagamento do cartão. Valor e status de pagamento vêm da
 * própria transacaoDespesa — não são duplicados aqui.
 */
@Entity
@Table(name = "faturas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cartao_id", nullable = false)
    private Cartao cartao;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_fechamento", nullable = false)
    private LocalDate dataFechamento;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "transacao_despesa_id", nullable = false)
    private Transacao transacaoDespesa;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;
}
