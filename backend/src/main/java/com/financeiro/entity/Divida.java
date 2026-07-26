package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDate;
import com.financeiro.entity.converter.ConversorLocalDateTime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "Cabeçalho" de um acordo de dívida parcelada — as parcelas em si são
 * {@link Transacao} normais (DESPESA/DEBITO), geradas na criação e
 * compartilhando {@code grupoParcelaId} com esta Divida. Reaproveita toda a
 * máquina existente de status/pagamento/cancelamento de transação; esta
 * entidade só agrupa e rotula (credor, descrição) para a tela de acompanhamento.
 */
@Entity
@Table(name = "dividas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Divida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    private String credor;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "total_parcelas", nullable = false)
    private Integer totalParcelas;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(name = "grupo_parcela_id", nullable = false)
    private String grupoParcelaId;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_cancelamento")
    private LocalDate dataCancelamento;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
