package com.financeiro.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class FluxoDeCaixaDTO {
    private BigDecimal saldoAtual;
    private List<Ponto> pontos;

    @Data
    @Builder
    public static class Ponto {
        private LocalDate data;
        private BigDecimal saldo;
        private BigDecimal saldoSimulado;
    }
}
