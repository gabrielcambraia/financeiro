package com.financeiro.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ItemFaturaDTO {
    private Long id;

    @NotNull
    private Long cartaoId;

    private Long categoriaId;

    @NotNull
    @Positive
    private BigDecimal valor;

    private String descricao;

    @NotNull
    private LocalDate data;

    private Integer totalParcelas;

    // campos de resposta
    private CategoriaDTO categoria;
    private Integer numeroParcela;
    private String grupoParcelaId;
    private Long faturaId;
    private LocalDate dataCancelamento;
    private boolean cancelado;
    private boolean faturado;
    // Usados pela listagem unificada de Lançamentos, que mescla itens de
    // fatura em aberto com transações — precisa mostrar de qual cartão/conta
    // é a compra sem uma segunda chamada.
    private String cartaoNome;
    private String cartaoCor;
    private Long contaPagamentoId;
    private String contaPagamentoNome;
}
