package com.financeiro.dto;

import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RecorrenciaDTO {

    private Long id;

    @NotNull
    private TipoTransacao tipo;

    @NotNull
    private TipoPagamento tipoPagamento;

    private Long contaId;
    private Long cartaoId;
    private Long categoriaId;
    private Long centroCustoId;

    @NotNull
    @Positive
    private BigDecimal valor;

    private String descricao;

    @NotNull
    @Min(1) @Max(31)
    private Integer diaCompetencia;

    @Min(1) @Max(31)
    private Integer diaVencimento;

    private boolean debitoAutomatico;

    private boolean ativa;

    @NotNull
    private LocalDate dataInicio;

    private LocalDate dataFim;

    // campos de resposta
    private String contaNome;
    private String cartaoNome;
    private String cartaoCor;
    private String categoriaNome;
    private String categoriaIcone;
    private String categoriaCor;
    private String centroCustoNome;
    private String centroCustoCor;
    private String proximaGeracaoMes;
    private String ultimaGeracaoMes;
}
