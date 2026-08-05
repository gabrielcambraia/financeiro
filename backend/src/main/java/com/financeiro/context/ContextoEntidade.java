package com.financeiro.context;

/**
 * Resolve qual entidade está ativa para o filtro da operação corrente.
 * {@code null} significa "Todas as entidades" (sem filtro).
 */
public interface ContextoEntidade {
    Long entidadeAtual();
}
