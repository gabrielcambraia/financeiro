package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDate;
import com.financeiro.entity.converter.ConversorLocalDateTime;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Meta de poupança. {@code valorAtual} é mantido incrementalmente (mesmo
 * padrão de {@link Conta#getSaldo()}) via aportar/resgatar em MetaService —
 * cada aporte/resgate também gera uma Transacao real (débito/crédito numa
 * conta), ligada de volta pela FK {@code meta_id} em transacoes.
 */
@Entity
@Table(name = "metas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "valor_alvo", nullable = false)
    private BigDecimal valorAlvo;

    @Builder.Default
    @Column(name = "valor_atual", nullable = false)
    private BigDecimal valorAtual = BigDecimal.ZERO;

    @Convert(converter = ConversorLocalDate.class)
    private LocalDate prazo;

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

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
