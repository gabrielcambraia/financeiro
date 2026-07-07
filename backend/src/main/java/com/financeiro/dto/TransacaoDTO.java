package com.financeiro.dto;

import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransacaoDTO {
    private Long id;

    @NotNull
    private Long contaId;

    private Long categoriaId;

    @NotNull
    private TipoTransacao tipo;

    @NotNull
    private TipoPagamento tipoPagamento;

    @NotNull
    @Positive
    private BigDecimal valor;

    private String descricao;

    @NotNull
    private LocalDate data;

    private boolean fixa;

    private Integer totalParcelas;

    // campos de resposta
    private ContaDTO conta;
    private CategoriaDTO categoria;
    private Integer numeroParcela;
    private String grupoParcelaId;
    private Long usuarioId;
}
