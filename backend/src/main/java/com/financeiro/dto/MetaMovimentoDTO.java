package com.financeiro.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MetaMovimentoDTO {
    @NotNull
    @Positive
    private BigDecimal valor;

    @NotNull
    private Long contaId;

    // Aporte/resgate é sempre uma ação já realizada — se vazio, usa hoje.
    private LocalDate data;
}
