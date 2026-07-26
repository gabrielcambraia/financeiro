package com.financeiro.seguranca;

import com.financeiro.entity.enums.NivelAcesso;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verificação de papel global (não por espaço), exposta como bean SpEL para
 * uso em {@code @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")}. Hoje só
 * protege o cadastro de bancos.
 */
@Component("autorizacaoAdmin")
public class AutorizacaoAdmin {

    public boolean exigirAdmin() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.nivelAcesso() == NivelAcesso.ADMIN) {
            return true;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas administradores podem realizar esta ação");
    }
}
