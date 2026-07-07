package com.financeiro.service;

import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RequisicaoTrocarSenha;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.entity.Espaco;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.UsuarioEspacoId;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.entity.enums.PlanoEspaco;
import com.financeiro.entity.enums.TipoEspaco;
import com.financeiro.repository.EspacoRepository;
import com.financeiro.repository.UsuarioEspacoRepository;
import com.financeiro.repository.UsuarioRepository;
import com.financeiro.seguranca.ServicoJwt;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Orquestra registro e login. O registro cria, numa única transação, o
 * usuário, o espaço pessoal dele, o vínculo (DONO) e as categorias padrão —
 * se algo falhar no meio, nada fica órfão.
 */
@Service
public class ServicoAutenticacao {

    private final UsuarioRepository usuarioRepository;
    private final EspacoRepository espacoRepository;
    private final UsuarioEspacoRepository usuarioEspacoRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServicoJwt servicoJwt;
    private final SemeadorCategoriasPadrao semeadorCategoriasPadrao;
    private final ContextoUsuario contextoUsuario;

    public ServicoAutenticacao(
            UsuarioRepository usuarioRepository,
            EspacoRepository espacoRepository,
            UsuarioEspacoRepository usuarioEspacoRepository,
            PasswordEncoder passwordEncoder,
            ServicoJwt servicoJwt,
            SemeadorCategoriasPadrao semeadorCategoriasPadrao,
            ContextoUsuario contextoUsuario) {
        this.usuarioRepository = usuarioRepository;
        this.espacoRepository = espacoRepository;
        this.usuarioEspacoRepository = usuarioEspacoRepository;
        this.passwordEncoder = passwordEncoder;
        this.servicoJwt = servicoJwt;
        this.semeadorCategoriasPadrao = semeadorCategoriasPadrao;
        this.contextoUsuario = contextoUsuario;
    }

    @Transactional
    public RespostaAutenticacao registrar(RequisicaoRegistro requisicao) {
        if (usuarioRepository.findByEmail(requisicao.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nome(requisicao.getNome())
                .email(requisicao.getEmail())
                .senhaHash(passwordEncoder.encode(requisicao.getSenha()))
                .build());

        Espaco espaco = espacoRepository.save(Espaco.builder()
                .nome(requisicao.getNome())
                .tipo(TipoEspaco.PESSOAL)
                .plano(PlanoEspaco.GRATUITO)
                .build());

        usuarioEspacoRepository.save(UsuarioEspaco.builder()
                .id(new UsuarioEspacoId(usuario.getId(), espaco.getId()))
                .papel(PapelUsuario.DONO)
                .build());

        semeadorCategoriasPadrao.semear(espaco.getId());

        String token = servicoJwt.gerarToken(usuario.getId(), espaco.getId(), usuario.getEmail(), false);
        return new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                espaco.getId(), PapelUsuario.DONO, false);
    }

    public RespostaAutenticacao login(RequisicaoLogin requisicao) {
        Usuario usuario = usuarioRepository.findByEmail(requisicao.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(requisicao.getSenha(), usuario.getSenhaHash())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        UsuarioEspaco vinculo = usuarioEspacoRepository.findByIdUsuarioId(usuario.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Usuário sem espaço vinculado"));

        String token = servicoJwt.gerarToken(usuario.getId(), vinculo.getId().getEspacoId(), usuario.getEmail(),
                usuario.isPrecisaTrocarSenha());
        return new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                vinculo.getId().getEspacoId(), vinculo.getPapel(), usuario.isPrecisaTrocarSenha());
    }

    @Transactional
    public RespostaAutenticacao trocarSenha(RequisicaoTrocarSenha requisicao) {
        Usuario usuario = usuarioRepository.findById(contextoUsuario.usuarioAtual())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));

        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(requisicao.getSenhaAtual(), usuario.getSenhaHash())) {
            throw new BadCredentialsException("Senha atual incorreta");
        }

        usuario.setSenhaHash(passwordEncoder.encode(requisicao.getNovaSenha()));
        usuario.setPrecisaTrocarSenha(false);
        usuarioRepository.save(usuario);

        UsuarioEspaco vinculo = usuarioEspacoRepository.findByIdUsuarioId(usuario.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Usuário sem espaço vinculado"));

        String token = servicoJwt.gerarToken(usuario.getId(), vinculo.getId().getEspacoId(), usuario.getEmail(), false);
        return new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                vinculo.getId().getEspacoId(), vinculo.getPapel(), false);
    }
}
