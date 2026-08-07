package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDate;
import com.financeiro.entity.converter.ConversorLocalDateTime;
import com.financeiro.entity.enums.DirecaoTransferencia;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Convert(converter = ConversorLocalDate.class)
    @Column(nullable = false)
    private LocalDate data;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_cancelamento")
    private LocalDate dataCancelamento;

    @Column(name = "fixa", nullable = false)
    private boolean fixa;

    @Builder.Default
    @Column(name = "saldo_ajustado", nullable = false)
    private boolean saldoAjustado = true;

    // Informada manualmente ao pagar uma despesa em atraso (PATCH /pagar).
    // Nulo = sem multa. Só se aplica a DESPESA — ver TransacaoService.computeDelta.
    private BigDecimal multa;

    @Column(name = "total_parcelas")
    private Integer totalParcelas;

    @Column(name = "numero_parcela")
    private Integer numeroParcela;

    @Column(name = "grupo_parcela_id")
    private String grupoParcelaId;

    // Preenchidos só quando tipo == TRANSFERENCIA: as duas linhas de uma
    // transferência (saída na conta origem, entrada na conta destino)
    // compartilham o mesmo transferenciaId e são sempre tratadas em par
    // (pagar/estornar/cancelar/excluir propagam para a linha irmã).
    @Column(name = "transferencia_id")
    private String transferenciaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direcao_transferencia")
    private DirecaoTransferencia direcaoTransferencia;

    // Preenchido só em aportes/resgates de meta (ver MetaService) — liga a
    // transação de volta à meta que a originou, sem precisar de um razão à parte.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "meta_id")
    private Meta meta;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "entidade_id")
    private Long entidadeId;

    // Identificador da série de transações fixas: aponta para o id da linha
    // mais antiga do grupo ("cabeça"). A própria cabeça aponta para si mesma.
    // Nulo apenas para fixas criadas antes da V23 que não foram processadas
    // pelo backfill (não deve ocorrer em produção após a migration).
    @Column(name = "origem_fixa_id")
    private Long origemFixaId;

    // false quando o trilho foi encerrado por uma edição FUTURAS — o agendador
    // ignora estas linhas ao estender a série para meses futuros.
    @Builder.Default
    @Column(name = "serie_ativa", nullable = false)
    private boolean serieAtiva = true;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.dataVencimento == null) {
            this.dataVencimento = this.data;
        }
    }
}
