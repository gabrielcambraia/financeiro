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

    // campo de resposta: saldo é derivado do saldo inicial + movimentações; ContaService
    // ignora esse valor tanto em create() (usa saldoInicial) quanto em update() (não altera saldo)
    private BigDecimal saldo;

    @NotNull
    private BigDecimal saldoInicial;

    @NotBlank
    private String cor;

    @NotBlank
    private String icone;

    private Long bancoId;

    // campo de resposta
    private BancoDTO banco;
}
