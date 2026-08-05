package com.financeiro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Estimativa (NÃO é cálculo fiscal oficial — só para exibir na UI) do IR
 * devido sobre o rendimento de um ativo de renda fixa, aplicando a tabela
 * regressiva brasileira sobre o prazo médio ponderado dos aportes.
 */
public final class  CalculadoraIr {

    private static final BigDecimal ALIQUOTA_ATE_180_DIAS = new BigDecimal("0.225");
    private static final BigDecimal ALIQUOTA_ATE_360_DIAS = new BigDecimal("0.20");
    private static final BigDecimal ALIQUOTA_ATE_720_DIAS = new BigDecimal("0.175");
    private static final BigDecimal ALIQUOTA_ACIMA_720_DIAS = new BigDecimal("0.15");

    private CalculadoraIr() {
    }

    /** Um aporte usado só para ponderar o prazo médio (data + valor aportado). */
    public record Aporte(LocalDate data, BigDecimal valor) {
    }

    /** Alíquota da tabela regressiva de renda fixa, dado o prazo em dias. */
    public static BigDecimal aliquota(long diasDePrazo) {
        if (diasDePrazo <= 180) {
            return ALIQUOTA_ATE_180_DIAS;
        }
        if (diasDePrazo <= 360) {
            return ALIQUOTA_ATE_360_DIAS;
        }
        if (diasDePrazo <= 720) {
            return ALIQUOTA_ATE_720_DIAS;
        }
        return ALIQUOTA_ACIMA_720_DIAS;
    }

    /** Prazo médio ponderado (em dias) entre a data de cada aporte e {@code hoje}, ponderado pelo valor aportado. */
    public static long prazoMedioPonderadoDias(List<Aporte> aportes, LocalDate hoje) {
        BigDecimal totalAportado = aportes.stream().map(Aporte::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAportado.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal somaPonderada = BigDecimal.ZERO;
        for (Aporte aporte : aportes) {
            long dias = ChronoUnit.DAYS.between(aporte.data(), hoje);
            somaPonderada = somaPonderada.add(aporte.valor().multiply(BigDecimal.valueOf(Math.max(dias, 0))));
        }
        return somaPonderada.divide(totalAportado, 0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * IR estimado sobre {@code totalRendimento}, dado o prazo médio ponderado
     * dos aportes. Zero se isento (LCI/LCA/poupança) ou se não há rendimento
     * positivo a tributar. É só uma estimativa exibida na UI — não substitui
     * o cálculo real feito pela corretora/instituição no resgate.
     */
    public static BigDecimal irEstimado(BigDecimal totalRendimento, long prazoMedioPonderadoDias, boolean isentoIr) {
        if (isentoIr || totalRendimento == null || totalRendimento.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalRendimento.multiply(aliquota(prazoMedioPonderadoDias)).setScale(2, RoundingMode.HALF_UP);
    }
}
