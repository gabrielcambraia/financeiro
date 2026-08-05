package com.financeiro.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartaoDTO {
    private Long id;

    @NotBlank
    private String nome;

    @NotNull
    @Positive
    private BigDecimal limite;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer diaFechamento;

    @NotNull
    @Min(1)
    @Max(31)
    private Integer diaVencimento;

    @NotNull
    private Long contaPagamentoId;

    @NotBlank
    private String cor;

    @NotBlank
    private String icone;

    private Long bancoId;

    private Long entidadeId;

    // campos de resposta
    private ContaDTO contaPagamento;
    private BancoDTO banco;
    private BigDecimal faturaAtualTotal;
    private BigDecimal limiteDisponivel;
}
