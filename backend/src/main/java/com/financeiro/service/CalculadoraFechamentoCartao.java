package com.financeiro.service;

import com.financeiro.entity.Cartao;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Calcula a data de fechamento de fatura de um cartão num mês qualquer —
 * usada para delimitar o ciclo de faturamento (fechamento do mês anterior,
 * exclusive, até o fechamento do mês alvo, inclusive) na navegação de itens
 * em aberto por mês (ver ItemFaturaService.findAbertosPorCiclo).
 */
final class CalculadoraFechamentoCartao {

    private CalculadoraFechamentoCartao() {
    }

    static LocalDate fechamentoDoMes(Cartao cartao, YearMonth mes) {
        return mes.atDay(Math.min(cartao.getDiaFechamento(), mes.lengthOfMonth()));
    }
}
