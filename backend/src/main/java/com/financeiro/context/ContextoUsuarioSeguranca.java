package com.financeiro.context;

import com.financeiro.seguranca.UsuarioAutenticado;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Implementação usada no perfil "nuvem": resolve o usuário autenticado a
 * partir do {@link UsuarioAutenticado} populado pelo filtro JWT, espelhando
 * {@link ContextoEspacoSeguranca}.
 */
@Component
@Profile("nuvem")
public class ContextoUsuarioSeguranca implements ContextoUsuario {

    @Override
    public Long usuarioAtual() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new IllegalStateException("Nenhum usuário autenticado no contexto de segurança.");
        }
        return usuario.usuarioId();
    }
}
