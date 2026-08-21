package com.financeiro.context;

/**
 * Resolve qual filial está ativa para o filtro da operação corrente.
 * {@code null} significa "Todas as filiais" (sem filtro).
 */
public interface ContextoFilial {
    Long filialAtual();
}
