package com.financeiro.dto;

import com.financeiro.entity.enums.TipoAtivo;
import com.financeiro.entity.enums.TipoRemuneracao;
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

    // rendimento automático (ver TipoRemuneracao) — opcionais; default é NENHUMA (100% manual)
    private TipoRemuneracao remuneracaoTipo;
    private BigDecimal taxa;
    private LocalDate inicioRendimento;
    private boolean isentoIr;

    private Long entidadeId;

    // aporte inicial opcional na criação — vira um aporte normal (debita a conta
    // vinculada) logo após o ativo ser criado; ver AtivoService.create()
    private BigDecimal valorInicial;
    private LocalDate dataInicial;

    // campos de resposta
    private ContaDTO conta;
    private BigDecimal valorAtual;
    private double percentualCarteira;
    private LocalDate dataCancelamento;
    private LocalDate rendidoAte;

    // campos derivados (rentabilidade/IR) — calculados em AtivoService.findAll(),
    // sem coluna nova no banco; irEstimado é só estimativa exibida na UI.
    private BigDecimal totalAportado;
    private BigDecimal totalResgatado;
    private BigDecimal totalRendimento;
    private Double rentabilidadePercentual;
    private BigDecimal irEstimado;
    private BigDecimal valorLiquidoEstimado;
}
