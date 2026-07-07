package com.financeiro.context;

/**
 * Resolve qual usuário está autenticado na operação corrente. Mesmo padrão
 * de {@link ContextoEspaco}: um seam com uma implementação por perfil, para
 * que services fiquem agnósticos de como a identidade é obtida.
 */
public interface ContextoUsuario {
    Long usuarioAtual();
}
