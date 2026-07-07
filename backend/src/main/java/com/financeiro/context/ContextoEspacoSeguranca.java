package com.financeiro.context;

import com.financeiro.seguranca.UsuarioAutenticado;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Implementação usada no perfil "nuvem": resolve o espaço ativo a partir do
 * {@link UsuarioAutenticado} autenticado via JWT (ver
 * {@code FiltroAutenticacaoJwt}), em vez de um valor fixo.
 */
@Component
@Profile("nuvem")
public class ContextoEspacoSeguranca implements ContextoEspaco {

    @Override
    public Long espacoAtual() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new IllegalStateException("Nenhum usuário autenticado no contexto de segurança.");
        }
        return usuario.espacoId();
    }
}
