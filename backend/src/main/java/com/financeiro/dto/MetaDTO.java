package com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MetaDTO {
    private Long id;

    @NotBlank
    private String nome;

    @NotNull
    @Positive
    private BigDecimal valorAlvo;

    private LocalDate prazo;

    @NotBlank
    private String cor;

    @NotBlank
    private String icone;

    // campos de resposta
    private BigDecimal valorAtual;
    private LocalDate dataCancelamento;
    private double percentualConcluido;
    private Integer mesesEstimados;
    private boolean concluida;
}
