package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.RequisicaoAdicionarMembro;
import com.financeiro.dto.RespostaMembro;
import com.financeiro.dto.RespostaMembroAdicionado;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.UsuarioEspacoId;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.repository.UsuarioEspacoRepository;
import com.financeiro.repository.UsuarioRepository;
import com.financeiro.seguranca.GeradorSenhaTemporaria;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Permite que o DONO de um espaço adicione membros. Diferente do registro
 * (que cria usuário + espaço próprio), aqui o novo usuário entra como MEMBRO
 * no espaço já existente do DONO — nunca ganha espaço nem categorias próprias.
 */
@Service
public class ServicoMembroEspaco {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioEspacoRepository usuarioEspacoRepository;
    private final PasswordEncoder passwordEncoder;
    private final GeradorSenhaTemporaria geradorSenhaTemporaria;
    private final ContextoEspaco contextoEspaco;

    public ServicoMembroEspaco(
            UsuarioRepository usuarioRepository,
            UsuarioEspacoRepository usuarioEspacoRepository,
            PasswordEncoder passwordEncoder,
            GeradorSenhaTemporaria geradorSenhaTemporaria,
            ContextoEspaco contextoEspaco) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioEspacoRepository = usuarioEspacoRepository;
        this.passwordEncoder = passwordEncoder;
        this.geradorSenhaTemporaria = geradorSenhaTemporaria;
        this.contextoEspaco = contextoEspaco;
    }

    @Transactional
    @PreAuthorize("@autorizacaoEspaco.exigirDono('Somente o dono do espaço pode adicionar membros')")
    public RespostaMembroAdicionado adicionar(RequisicaoAdicionarMembro requisicao) {
        Long espacoId = contextoEspaco.espacoAtual();

        if (usuarioRepository.findByEmail(requisicao.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já usado");
        }

        String senhaTemporaria = geradorSenhaTemporaria.gerar();
        Usuario membro = usuarioRepository.save(Usuario.builder()
                .nome(requisicao.getNome())
                .email(requisicao.getEmail())
                .senhaHash(passwordEncoder.encode(senhaTemporaria))
                .precisaTrocarSenha(true)
                .build());

        usuarioEspacoRepository.save(UsuarioEspaco.builder()
                .id(new UsuarioEspacoId(membro.getId(), espacoId))
                .papel(PapelUsuario.MEMBRO)
                .build());

        return new RespostaMembroAdicionado(membro.getId(), membro.getNome(), membro.getEmail(),
                PapelUsuario.MEMBRO, senhaTemporaria);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@autorizacaoEspaco.exigirDono('Somente o dono do espaço pode listar membros')")
    public List<RespostaMembro> listar() {
        Long espacoId = contextoEspaco.espacoAtual();
        return usuarioEspacoRepository.listarMembrosDoEspaco(espacoId);
    }
}
