package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDate;
import com.financeiro.entity.converter.ConversorLocalDateTime;
import com.financeiro.entity.enums.TipoAtivo;
import com.financeiro.entity.enums.TipoRemuneracao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Investimento (reserva, renda fixa ou variável). {@code valorAtual} é
 * mantido incrementalmente (mesmo padrão de Conta.saldo e Meta.valorAtual)
 * via AtivoService.aportar/resgatar/registrarRendimento.
 */
@Entity
@Table(name = "ativos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoAtivo tipo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @Builder.Default
    @Column(name = "valor_atual", nullable = false)
    private BigDecimal valorAtual = BigDecimal.ZERO;

    @Column(nullable = false)
    private String cor;

    @Column(nullable = false)
    private String icone;

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

    @Column(name = "entidade_id")
    private Long entidadeId;

    // ---------- rendimento automático (ver AgendadorRendimento) ----------

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "remuneracao_tipo", nullable = false)
    private TipoRemuneracao remuneracaoTipo = TipoRemuneracao.NENHUMA;

    /** Significado depende de {@link #remuneracaoTipo} — ver javadoc do enum. */
    @Column(name = "taxa", precision = 9, scale = 4)
    private BigDecimal taxa;

    /** Último mês (fim de mês) já creditado pelo agendador — chave da idempotência. */
    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "rendido_ate")
    private LocalDate rendidoAte;

    /** A partir de quando o ativo passa a render (default: data do primeiro aporte). */
    @Convert(converter = ConversorLocalDate.class)
    @Column(name = "inicio_rendimento")
    private LocalDate inicioRendimento;

    /** LCI/LCA/poupança etc. — zera o IR estimado exibido na UI. */
    @Builder.Default
    @Column(name = "isento_ir", nullable = false)
    private boolean isentoIr = false;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
