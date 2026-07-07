package com.financeiro.context;

/**
 * Resolve qual espaço (tenant) está ativo para a operação corrente. Hoje
 * (sem autenticação) sempre retorna o espaço padrão do modo desktop-local.
 * Quando a autenticação (JWT) for adicionada, a implementação passa a ler o
 * espaço a partir do contexto de segurança — este é o único ponto que
 * precisará mudar.
 */
public interface ContextoEspaco {
    Long espacoAtual();
}
