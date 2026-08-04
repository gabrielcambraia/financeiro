package com.financeiro;

import com.financeiro.service.CalculadoraIr;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa {@link CalculadoraIr} isoladamente (sem Spring) — uma asserção por
 * faixa da tabela regressiva de IR de renda fixa, mais o caso isento.
 * É só uma estimativa exibida na UI, nunca o cálculo fiscal oficial.
 */
class CalculadoraIrTest {

    @Test
    void aliquota_ate180Dias_22e5PorCento() {
        assertThat(CalculadoraIr.aliquota(180)).isEqualByComparingTo("0.225");
        assertThat(CalculadoraIr.aliquota(1)).isEqualByComparingTo("0.225");
    }

    @Test
    void aliquota_ate360Dias_20PorCento() {
        assertThat(CalculadoraIr.aliquota(181)).isEqualByComparingTo("0.20");
        assertThat(CalculadoraIr.aliquota(360)).isEqualByComparingTo("0.20");
    }

    @Test
    void aliquota_ate720Dias_17e5PorCento() {
        assertThat(CalculadoraIr.aliquota(361)).isEqualByComparingTo("0.175");
        assertThat(CalculadoraIr.aliquota(720)).isEqualByComparingTo("0.175");
    }

    @Test
    void aliquota_acima720Dias_15PorCento() {
        assertThat(CalculadoraIr.aliquota(721)).isEqualByComparingTo("0.15");
        assertThat(CalculadoraIr.aliquota(3650)).isEqualByComparingTo("0.15");
    }

    @Test
    void irEstimado_isento_retornaZero() {
        BigDecimal ir = CalculadoraIr.irEstimado(BigDecimal.valueOf(500), 400, true);
        assertThat(ir).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void irEstimado_naoIsento_aplicaAliquotaDaFaixa() {
        BigDecimal ir = CalculadoraIr.irEstimado(BigDecimal.valueOf(1000), 100, false);
        assertThat(ir).isEqualByComparingTo("225.00"); // 22,5% de 1000
    }

    @Test
    void irEstimado_semRendimentoPositivo_retornaZero() {
        assertThat(CalculadoraIr.irEstimado(BigDecimal.ZERO, 100, false)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(CalculadoraIr.irEstimado(BigDecimal.valueOf(-10), 100, false)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(CalculadoraIr.irEstimado(null, 100, false)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void prazoMedioPonderado_ponderaPeloValorDeCadaAporte() {
        var hoje = java.time.LocalDate.of(2026, 1, 1);
        var aportes = java.util.List.of(
                new CalculadoraIr.Aporte(hoje.minusDays(100), BigDecimal.valueOf(100)),
                new CalculadoraIr.Aporte(hoje.minusDays(300), BigDecimal.valueOf(300))
        );
        // média ponderada: (100*100 + 300*300) / 400 = (10000 + 90000)/400 = 250
        assertThat(CalculadoraIr.prazoMedioPonderadoDias(aportes, hoje)).isEqualTo(250);
    }
}
