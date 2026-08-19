package com.financeiro.dto;

import com.financeiro.entity.enums.StatusDivida;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DividaDTO {
    private Long id;

    @NotBlank
    private String descricao;

    private String credor;

    @NotNull
    @Positive
    private BigDecimal valorTotal;

    @NotNull
    @Min(1)
    @Max(360)
    private Integer totalParcelas;

    @NotNull
    private Long contaId;

    private Long categoriaId;

    @NotNull
    private LocalDate dataInicio;

    // campos de resposta
    private ContaDTO conta;
    private CategoriaDTO categoria;
    private LocalDate dataCancelamento;
    private BigDecimal valorPago;
    private BigDecimal valorRestante;
    private int parcelasPagas;
    private StatusDivida status;
    private LocalDate proximaParcelaData;
    private BigDecimal proximaParcelaValor;
}
