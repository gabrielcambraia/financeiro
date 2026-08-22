package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.RequisicaoAtualizarUsuario;
import com.financeiro.dto.RequisicaoCriarUsuario;
import com.financeiro.dto.RespostaUsuario;
import com.financeiro.dto.RespostaUsuarioCriado;
import com.financeiro.entity.Usuario;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.UsuarioRepository;
import com.financeiro.seguranca.GeradorSenhaTemporaria;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoUsuario {

    private final UsuarioRepository usuarioRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;
    private final GeradorSenhaTemporaria geradorSenhaTemporaria;
    private final PasswordEncoder passwordEncoder;

    @PreAuthorize("@autorizacaoEspaco.exigirDono('Apenas o dono pode gerenciar usuários')")
    public List<RespostaUsuario> listar() {
        return usuarioRepository.findByEspacoIdOrderByPapelAscNomeAsc(contextoEspaco.espacoAtual())
                .stream().map(this::mapear).toList();
    }

    @Transactional
    @PreAuthorize("@autorizacaoEspaco.exigirDono('Apenas o dono pode criar usuários')")
    public RespostaUsuarioCriado criar(RequisicaoCriarUsuario requisicao) {
        if (usuarioRepository.findByEmail(requisicao.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        String senhaTemporaria = geradorSenhaTemporaria.gerar();
        Usuario usuario;
        try {
            usuario = usuarioRepository.save(Usuario.builder()
                    .nome(requisicao.getNome())
                    .email(requisicao.getEmail())
                    .telefone(requisicao.getTelefone())
                    .senhaHash(passwordEncoder.encode(senhaTemporaria))
                    .espacoId(contextoEspaco.espacoAtual())
                    .papel(requisicao.getPapel())
                    .precisaTrocarSenha(true)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Cobre a corrida entre o findByEmail acima e este save() — dois
            // cadastros concorrentes para o mesmo e-mail (aqui ou em
            // ServicoAutenticacao.registrar()) só um vence a constraint
            // unique; o outro deve virar 409, não um 500 genérico.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        RespostaUsuario base = mapear(usuario);
        return new RespostaUsuarioCriado(base.id(), base.nome(), base.email(), base.papel(), base.criadoEm(),
                senhaTemporaria);
    }

    @Transactional
    @PreAuthorize("@autorizacaoEspaco.exigirDono('Apenas o dono pode editar usuários')")
    public RespostaUsuario atualizar(Long id, RequisicaoAtualizarUsuario requisicao) {
        Long espacoId = contextoEspaco.espacoAtual();
        Usuario usuario = usuarioRepository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Usuário não encontrado: " + id));

        // Um DONO nunca pode alterar o próprio papel — é a única barreira que
        // impede um espaço de ficar sem nenhum DONO (não há outro caminho que
        // remova um DONO existente). Nome/e-mail/telefone continuam editáveis
        // para si mesmo.
        boolean ehSiMesmo = usuario.getId().equals(contextoUsuario.usuarioAtual());
        if (ehSiMesmo && requisicao.getPapel() != usuario.getPapel()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível alterar o próprio papel");
        }

        if (!requisicao.getEmail().equalsIgnoreCase(usuario.getEmail())
                && usuarioRepository.findByEmail(requisicao.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        usuario.setNome(requisicao.getNome());
        usuario.setEmail(requisicao.getEmail());
        usuario.setTelefone(requisicao.getTelefone());
        usuario.setPapel(requisicao.getPapel());

        try {
            return mapear(usuarioRepository.save(usuario));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
    }

    private RespostaUsuario mapear(Usuario u) {
        return new RespostaUsuario(u.getId(), u.getNome(), u.getEmail(), u.getTelefone(), u.getPapel(), u.getCriadoEm());
    }
}
