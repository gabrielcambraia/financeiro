package com.financeiro.dto;

import com.financeiro.entity.enums.TipoAtivo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AtivoDTO {
    private Long id;

    @NotBlank
    private String nome;

    @NotNull
    private TipoAtivo tipo;

    @NotNull
    private Long contaId;

    @NotBlank
    private String cor;

    @NotBlank
    private String icone;

    // campos de resposta
    private ContaDTO conta;
    private BigDecimal valorAtual;
    private double percentualCarteira;
    private LocalDate dataCancelamento;
}
