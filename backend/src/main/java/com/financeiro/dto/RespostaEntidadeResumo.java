package com.financeiro.dto;

import com.financeiro.entity.enums.TipoPessoa;

/**
 * Resumo leve da entidade enviado junto da resposta de autenticação.
 * Não decifra o documento — apenas id, nome e tipo para popular o seletor
 * de entidade no frontend sem exigir outra chamada.
 */
public record RespostaEntidadeResumo(Long id, String nome, TipoPessoa tipoPessoa) {}
