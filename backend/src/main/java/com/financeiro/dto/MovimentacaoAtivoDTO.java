package com.financeiro.dto;

import com.financeiro.entity.enums.TipoMovimentacaoAtivo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MovimentacaoAtivoDTO {
    private Long id;
    private TipoMovimentacaoAtivo tipo;

    @NotNull
    @Positive
    private BigDecimal valor;

    // Aporte/resgate/rendimento é sempre uma ação já realizada — se vazio, usa hoje.
    private LocalDate data;

    // obrigatório só para aportar/resgatar (rendimento não move conta nenhuma)
    private Long contaId;
}
