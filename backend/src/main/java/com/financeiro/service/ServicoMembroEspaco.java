package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.RequisicaoAdicionarMembro;
import com.financeiro.dto.RespostaMembroAdicionado;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.UsuarioEspacoId;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.repository.UsuarioEspacoRepository;
import com.financeiro.repository.UsuarioRepository;
import com.financeiro.seguranca.GeradorSenhaTemporaria;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final ContextoUsuario contextoUsuario;

    public ServicoMembroEspaco(
            UsuarioRepository usuarioRepository,
            UsuarioEspacoRepository usuarioEspacoRepository,
            PasswordEncoder passwordEncoder,
            GeradorSenhaTemporaria geradorSenhaTemporaria,
            ContextoEspaco contextoEspaco,
            ContextoUsuario contextoUsuario) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioEspacoRepository = usuarioEspacoRepository;
        this.passwordEncoder = passwordEncoder;
        this.geradorSenhaTemporaria = geradorSenhaTemporaria;
        this.contextoEspaco = contextoEspaco;
        this.contextoUsuario = contextoUsuario;
    }

    @Transactional
    public RespostaMembroAdicionado adicionar(RequisicaoAdicionarMembro requisicao) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioAtualId = contextoUsuario.usuarioAtual();

        UsuarioEspaco vinculoAtual = usuarioEspacoRepository.findById(new UsuarioEspacoId(usuarioAtualId, espacoId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem vínculo com este espaço"));
        if (vinculoAtual.getPapel() != PapelUsuario.DONO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o dono do espaço pode adicionar membros");
        }

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
}
