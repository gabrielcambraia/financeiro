package com.financeiro.seguranca;

/**
 * Resultado de uma emissão/rotação de refresh token: o valor bruto (só
 * existe neste instante, nunca é persistido) e o par usuário/espaço a que
 * ele pertence, usado para reemitir o access token.
 */
public record TokenRenovado(String tokenBruto, Long usuarioId, Long espacoId) {
}
