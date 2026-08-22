package com.financeiro.dto;

import com.financeiro.entity.enums.TipoPessoa;

/**
 * Resumo leve da filial enviado junto da resposta de autenticação.
 * Não decifra o documento — apenas id, nome e tipo para popular o seletor
 * de filial no frontend sem exigir outra chamada.
 */
public record RespostaFilialResumo(Long id, String nome, TipoPessoa tipoPessoa) {}
