package com.financeiro.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PainelDTO {
    private BigDecimal totalReceitas;
    private BigDecimal totalDespesas;
    private BigDecimal saldoLiquido;
    private ResumoFluxo realizado;
    private ResumoFluxo pendente;
    private List<ResumoCategoria> despesasPorCategoria;
    private List<ResumoCategoria> receitasPorCategoria;
    private List<TendenciaMensal> tendenciaMensal;
    private List<SaldoConta> saldosContas;
    private List<SaldoDiario> saldoDiario;

    @Data
    @Builder
    public static class ResumoFluxo {
        private BigDecimal receita;
        private BigDecimal despesa;
        private BigDecimal saldo;
    }

    @Data
    @Builder
    public static class ResumoCategoria {
        private CategoriaDTO categoria;
        private BigDecimal total;
        private double percentual;
    }

    @Data
    @Builder
    public static class TendenciaMensal {
        private String mes;
        private BigDecimal receita;
        private BigDecimal despesa;
    }

    @Data
    @Builder
    public static class SaldoConta {
        private ContaDTO conta;
        private BigDecimal saldo;
    }

    @Data
    @Builder
    public static class SaldoDiario {
        private LocalDate data;
        private BigDecimal saldo;
    }
}
