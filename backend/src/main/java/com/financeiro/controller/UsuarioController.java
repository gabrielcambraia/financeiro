package com.financeiro.controller;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.RequisicaoAlterarPapel;
import com.financeiro.dto.RespostaUsuario;
import com.financeiro.entity.Usuario;
import com.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;

    @GetMapping
    @PreAuthorize("@autorizacaoEspaco.exigirDono('Apenas o dono pode gerenciar usuários')")
    public List<RespostaUsuario> listar() {
        return usuarioRepository.findByEspacoIdOrderByPapelAscNomeAsc(contextoEspaco.espacoAtual())
                .stream().map(this::mapear).toList();
    }

    @PatchMapping("/{id}/papel")
    @PreAuthorize("@autorizacaoEspaco.exigirDono('Apenas o dono pode alterar papéis')")
    public RespostaUsuario alterarPapel(@PathVariable Long id,
                                        @RequestBody RequisicaoAlterarPapel requisicao) {
        Long espacoId = contextoEspaco.espacoAtual();
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        if (!usuario.getEspacoId().equals(espacoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário não pertence a este espaço");
        }
        if (usuario.getId().equals(contextoUsuario.usuarioAtual())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível alterar o próprio papel");
        }

        usuario.setPapel(requisicao.getPapel());
        return mapear(usuarioRepository.save(usuario));
    }

    private RespostaUsuario mapear(Usuario u) {
        return new RespostaUsuario(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.getCriadoEm());
    }
}
