package com.financeiro.seguranca;

import com.financeiro.context.ContextoUsuario;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verificações de autorização por papel, expostas como bean SpEL
 * para uso em {@code @PreAuthorize} (ex.: {@code @autorizacaoEspaco.exigirDono(...)}).
 * O papel é sempre lido do banco, não do claim JWT — assim um rebaixamento de
 * papel feito por outro DONO (ver {@code UsuarioController.alterarPapel}) tem
 * efeito imediato, em vez de só valer quando o access token do usuário afetado
 * expirar.
 */
@Component("autorizacaoEspaco")
@RequiredArgsConstructor
public class AutorizacaoEspaco {

    private final ContextoUsuario contextoUsuario;
    private final UsuarioRepository usuarioRepository;

    public boolean exigirDono(String mensagemErro) {
        Long usuarioId;
        try {
            usuarioId = contextoUsuario.usuarioAtual();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não autenticado");
        }
        PapelUsuario papelAtual = usuarioRepository.findById(usuarioId)
                .map(Usuario::getPapel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Não autenticado"));
        if (papelAtual != PapelUsuario.DONO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensagemErro);
        }
        return true;
    }
}
