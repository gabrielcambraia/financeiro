package com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrcamentoDTO {
    private Long id;

    @NotNull
    private Long categoriaId;

    @NotBlank
    private String mes;

    @NotNull
    @Positive
    private BigDecimal limite;

    private Long filialId;

    // campos de resposta
    private CategoriaDTO categoria;
    private BigDecimal gasto;
    private double percentualUsado;
    private boolean estourado;
}
