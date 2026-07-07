package com.financeiro.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementação usada no modo desktop-local: sempre resolve para o usuário
 * padrão (id=1 por padrão), espelhando {@link ContextoEspacoPadrao}.
 */
@Component
@Profile("!nuvem")
public class ContextoUsuarioPadrao implements ContextoUsuario {

    @Value("${financeiro.usuario-padrao:1}")
    private Long usuarioPadrao;

    @Override
    public Long usuarioAtual() {
        return usuarioPadrao;
    }
}
