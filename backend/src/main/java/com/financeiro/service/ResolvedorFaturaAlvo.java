package com.financeiro.service;

import com.financeiro.entity.Cartao;
import com.financeiro.entity.Fatura;
import com.financeiro.repository.FaturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Dado um cartão e uma data de compra, resolve a Fatura à qual o item pertencerá:
 * a fatura cujo dataFechamento é o mais próximo >= data da compra. Retorna null
 * quando a fatura correspondente ainda não existe (período em aberto — o
 * AgendadorFatura criará a fatura no dia do fechamento).
 */
@Component
@RequiredArgsConstructor
public class ResolvedorFaturaAlvo {

    private final FaturaRepository faturaRepository;

    public Fatura resolver(Cartao cartao, LocalDate data) {
        YearMonth ym = YearMonth.from(data);
        // Itera até encontrar um fechamento >= data (compra pode cair após o fechamento do mês corrente)
        for (int i = 0; i < 24; i++) {
            LocalDate fechamento = CalculadoraFechamentoCartao.fechamentoDoMes(cartao, ym);
            if (!fechamento.isBefore(data)) {
                return faturaRepository.findByCartaoIdAndDataFechamento(cartao.getId(), fechamento).orElse(null);
            }
            ym = ym.plusMonths(1);
        }
        return null;
    }
}
