package com.financeiro.seguranca;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.UsuarioEspacoId;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.repository.UsuarioEspacoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verificações de autorização por vínculo/espaço, expostas como bean SpEL
 * para uso em {@code @PreAuthorize} (ex.: {@code @autorizacaoEspaco.exigirDono(...)}).
 */
@Component("autorizacaoEspaco")
public class AutorizacaoEspaco {

    private final UsuarioEspacoRepository usuarioEspacoRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;

    public AutorizacaoEspaco(
            UsuarioEspacoRepository usuarioEspacoRepository,
            ContextoEspaco contextoEspaco,
            ContextoUsuario contextoUsuario) {
        this.usuarioEspacoRepository = usuarioEspacoRepository;
        this.contextoEspaco = contextoEspaco;
        this.contextoUsuario = contextoUsuario;
    }

    public boolean exigirDono(String mensagemErro) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioAtualId = contextoUsuario.usuarioAtual();

        UsuarioEspaco vinculoAtual = usuarioEspacoRepository.findById(new UsuarioEspacoId(usuarioAtualId, espacoId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem vínculo com este espaço"));
        if (vinculoAtual.getPapel() != PapelUsuario.DONO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensagemErro);
        }
        return true;
    }
}
