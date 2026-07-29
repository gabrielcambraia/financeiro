package com.financeiro.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Vencimentos vencimentos;

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

    // aPagar/aReceber/aVencer force @JsonProperty explícito: getAPagar() etc.
    // caem no mangler legado do Jackson para nomes com duas maiúsculas
    // seguidas no início (getAPagar -> "apagar", não "aPagar"), então sem a
    // anotação o campo simplesmente some no JSON.
    @Data
    @Builder
    public static class Vencimentos {
        @JsonProperty("aPagar")
        private GrupoVencimento aPagar;
        @JsonProperty("aReceber")
        private GrupoVencimento aReceber;
    }

    @Data
    @Builder
    public static class GrupoVencimento {
        private BigDecimal totalVencido;
        private int quantidadeVencida;
        private List<ItemVencimento> vencidas;
        private BigDecimal totalAVencer;
        private int quantidadeAVencer;
        @JsonProperty("aVencer")
        private List<ItemVencimento> aVencer;
    }

    @Data
    @Builder
    public static class ItemVencimento {
        private Long id;
        private String descricao;
        private BigDecimal valor;
        private LocalDate dataVencimento;
        private long diasEmRelacaoAHoje;
        private String contaNome;
    }
}
