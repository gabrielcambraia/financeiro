package com.financeiro.service;

import com.financeiro.context.ContextoUsuario;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.PropositoCodigo;
import com.financeiro.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ServicoVerificacaoContato {

    private final ServicoCodigoVerificacao servicoCodigoVerificacao;
    private final UsuarioRepository usuarioRepository;
    private final ContextoUsuario contextoUsuario;

    @Transactional
    public void solicitarVerificacaoEmail(String ipOrigem) {
        Usuario usuario = usuarioAtual();
        servicoCodigoVerificacao.solicitar(
                usuario.getEmail(), CanalNotificacao.EMAIL, PropositoCodigo.VERIFICAR_EMAIL, ipOrigem, usuario.getId());
    }

    @Transactional
    public void confirmarVerificacaoEmail(String codigo) {
        Usuario usuario = usuarioAtual();
        servicoCodigoVerificacao.verificar(usuario.getEmail(), PropositoCodigo.VERIFICAR_EMAIL, codigo);
        usuario.setEmailVerificadoEm(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void solicitarVerificacaoTelefone(String ipOrigem) {
        Usuario usuario = usuarioAtual();
        if (usuario.getTelefone() == null || usuario.getTelefone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Nenhum telefone cadastrado");
        }
        servicoCodigoVerificacao.solicitar(
                usuario.getTelefone(), CanalNotificacao.SMS, PropositoCodigo.VERIFICAR_TELEFONE, ipOrigem, usuario.getId());
    }

    @Transactional
    public void confirmarVerificacaoTelefone(String codigo) {
        Usuario usuario = usuarioAtual();
        if (usuario.getTelefone() == null || usuario.getTelefone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Nenhum telefone cadastrado");
        }
        servicoCodigoVerificacao.verificar(usuario.getTelefone(), PropositoCodigo.VERIFICAR_TELEFONE, codigo);
        usuario.setTelefoneVerificadoEm(LocalDateTime.now());
        usuarioRepository.save(usuario);
    }

    private Usuario usuarioAtual() {
        return usuarioRepository.findById(contextoUsuario.usuarioAtual())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));
    }
}
