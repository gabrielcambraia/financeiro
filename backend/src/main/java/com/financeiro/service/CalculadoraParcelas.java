package com.financeiro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Divide o valor total de uma compra parcelada entre as parcelas. Usa
 * arredondamento para baixo em cada parcela e empurra o resto de centavos
 * (sempre menor que a quantidade de parcelas) para a última, garantindo que
 * a soma de todas as parcelas seja exatamente igual ao valor total.
 */
final class CalculadoraParcelas {

    private CalculadoraParcelas() {
    }

    static BigDecimal valorParcela(BigDecimal valorTotal, int totalParcelas, int numeroParcela) {
        BigDecimal base = valorTotal.divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.DOWN);
        if (numeroParcela != totalParcelas) {
            return base;
        }
        BigDecimal somaDemais = base.multiply(BigDecimal.valueOf(totalParcelas - 1));
        return valorTotal.subtract(somaDemais);
    }
}
