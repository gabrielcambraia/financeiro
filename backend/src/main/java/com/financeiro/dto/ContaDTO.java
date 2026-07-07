package com.financeiro.dto;

import com.financeiro.entity.enums.TipoConta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ContaDTO {
    private Long id;

    @NotBlank
    private String nome;

    @NotNull
    private TipoConta tipo;

    @NotNull
    private BigDecimal saldo;

    @NotBlank
    private String cor;

    @NotBlank
    private String icone;
}
