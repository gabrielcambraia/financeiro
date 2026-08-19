package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDate;
import com.financeiro.entity.converter.ConversorLocalDateTime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Compra individual no cartão. Nunca mexe em saldo de conta — enquanto
 * {@code fatura} for nulo, o item está "em aberto" (ainda não fechou);
 * o {@link com.financeiro.scheduler.AgendadorFatura} soma os itens abertos
 * de cada cartão no dia do fechamento e os associa à Fatura recém-criada.
 */
@Entity
@Table(name = "itens_fatura")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemFatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cartao_id", nullable = false)
    private Cartao cartao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fatura_id")
    private Fatura fatura;

    @Column(nullable = false)
    private BigDecimal valor;

    private String descricao;

    @Convert(converter = ConversorLocalDate.class)
    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "total_parcelas")
    private Integer totalParcelas;

    @Column(name = "numero_parcela")
    private Integer numeroParcela;

    @Column(name = "grupo_parcela_id")
    private String grupoParcelaId;

    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "data_cancelamento")
    private LocalDate dataCancelamento;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "fixa", nullable = false)
    private boolean fixa;

    @Column(name = "origem_fixa_id")
    private Long origemFixaId;

    @Builder.Default
    @Column(name = "serie_ativa", nullable = false)
    private boolean serieAtiva = true;

    // Aponta para a Recorrencia que gerou este item. Permanece preenchido
    // mesmo quando o item é editado localmente.
    @Column(name = "origem_recorrencia_id")
    private Long origemRecorrenciaId;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "centro_custo_id")
    private Long centroCustoId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "centro_custo_id", insertable = false, updatable = false)
    private CentroCusto centroCusto;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
