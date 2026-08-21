package com.financeiro.seguranca;

import com.financeiro.entity.enums.PapelUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verificações de autorização por papel, expostas como bean SpEL
 * para uso em {@code @PreAuthorize} (ex.: {@code @autorizacaoEspaco.exigirDono(...)}).
 * O papel vem do claim JWT — sem consulta ao banco.
 */
@Component("autorizacaoEspaco")
public class AutorizacaoEspaco {

    public boolean exigirDono(String mensagemErro) {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não autenticado");
        }
        if (usuario.papel() != PapelUsuario.DONO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensagemErro);
        }
        return true;
    }
}
