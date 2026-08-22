package com.financeiro.service;

import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.PrimeiraFilialDTO;
import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RequisicaoTrocarSenha;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaFilialResumo;
import com.financeiro.entity.Filial;
import com.financeiro.entity.Espaco;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.CodigoPlano;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.entity.enums.PlanoEspaco;
import com.financeiro.entity.enums.PropositoCodigo;
import com.financeiro.entity.enums.TipoEspaco;
import com.financeiro.repository.FilialRepository;
import com.financeiro.repository.EspacoRepository;
import com.financeiro.repository.UsuarioRepository;
import com.financeiro.seguranca.ServicoJwt;
import com.financeiro.seguranca.ServicoTokenAtualizacao;
import com.financeiro.seguranca.TokenRenovado;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Orquestra registro e login. O registro cria, numa única transação, o
 * usuário, o espaço pessoal dele e as categorias padrão — se algo falhar
 * no meio, nada fica órfão.
 */
@Slf4j
@Service
public class ServicoAutenticacao {

    private final UsuarioRepository usuarioRepository;
    private final EspacoRepository espacoRepository;
    private final FilialRepository filialRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServicoJwt servicoJwt;
    private final ServicoTokenAtualizacao servicoTokenAtualizacao;
    private final SemeadorCategoriasPadrao semeadorCategoriasPadrao;
    private final ServicoAssinatura servicoAssinatura;
    private final CifradorDados cifradorDados;
    private final ValidadorDocumento validadorDocumento;
    private final ServicoCodigoVerificacao servicoCodigoVerificacao;
    private final ContextoUsuario contextoUsuario;

    public ServicoAutenticacao(
            UsuarioRepository usuarioRepository,
            EspacoRepository espacoRepository,
            FilialRepository filialRepository,
            PasswordEncoder passwordEncoder,
            ServicoJwt servicoJwt,
            ServicoTokenAtualizacao servicoTokenAtualizacao,
            SemeadorCategoriasPadrao semeadorCategoriasPadrao,
            ServicoAssinatura servicoAssinatura,
            CifradorDados cifradorDados,
            ValidadorDocumento validadorDocumento,
            ServicoCodigoVerificacao servicoCodigoVerificacao,
            ContextoUsuario contextoUsuario) {
        this.usuarioRepository = usuarioRepository;
        this.espacoRepository = espacoRepository;
        this.filialRepository = filialRepository;
        this.passwordEncoder = passwordEncoder;
        this.servicoJwt = servicoJwt;
        this.servicoTokenAtualizacao = servicoTokenAtualizacao;
        this.semeadorCategoriasPadrao = semeadorCategoriasPadrao;
        this.servicoAssinatura = servicoAssinatura;
        this.cifradorDados = cifradorDados;
        this.validadorDocumento = validadorDocumento;
        this.servicoCodigoVerificacao = servicoCodigoVerificacao;
        this.contextoUsuario = contextoUsuario;
    }

    public record ResultadoAutenticacao(RespostaAutenticacao resposta, String tokenAtualizacaoBruto) {
    }

    @Transactional
    public ResultadoAutenticacao registrar(RequisicaoRegistro requisicao, String userAgent, String ipOrigem) {
        if (usuarioRepository.findByEmail(requisicao.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        Espaco espaco = espacoRepository.save(Espaco.builder()
                .nome(requisicao.getNome())
                .tipo(TipoEspaco.PESSOAL)
                .plano(PlanoEspaco.GRATUITO)
                .build());

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nome(requisicao.getNome())
                .email(requisicao.getEmail())
                .senhaHash(passwordEncoder.encode(requisicao.getSenha()))
                .telefone(requisicao.getTelefone())
                .espacoId(espaco.getId())
                .papel(PapelUsuario.DONO)
                .build());

        servicoAssinatura.criarParaEspaco(espaco.getId(), CodigoPlano.INDIVIDUAL);

        PrimeiraFilialDTO ent = requisicao.getEntidade();
        String docLimpo = validadorDocumento.limparEValidar(ent.getDocumento(), ent.getTipoPessoa());
        String hashDoc = cifradorDados.hashDocumento(docLimpo);
        filialRepository.save(Filial.builder()
                .espacoId(espaco.getId())
                .tipoPessoa(ent.getTipoPessoa())
                .nome(ent.getNome())
                .nomeFantasia(ent.getNomeFantasia())
                .documentoCifrado(cifradorDados.cifrar(docLimpo))
                .documentoHash(hashDoc)
                .inscricaoEstadual(ent.getInscricaoEstadual())
                .dataNascimento(ent.getDataNascimento())
                .email(ent.getEmail())
                .telefone(ent.getTelefone())
                .cep(ent.getCep())
                .logradouro(ent.getLogradouro())
                .numero(ent.getNumero())
                .complemento(ent.getComplemento())
                .bairro(ent.getBairro())
                .cidade(ent.getCidade())
                .uf(ent.getUf())
                .build());

        semeadorCategoriasPadrao.semear(espaco.getId());

        try {
            servicoCodigoVerificacao.solicitar(
                    usuario.getEmail(), CanalNotificacao.EMAIL, PropositoCodigo.VERIFICAR_EMAIL, ipOrigem, usuario.getId());
        } catch (ResponseStatusException e) {
            log.warn("Envio de código de verificação falhou no registro: {}", e.getReason());
        }

        String token = servicoJwt.gerarToken(usuario.getId(), espaco.getId(), usuario.getEmail(), false,
                telefonePendente(usuario), usuario.getNivelAcesso(), usuario.getPapel());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(usuario.getId(), espaco.getId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), espaco.getId(), usuario.getPapel(), false, telefonePendente(usuario),
                usuario.getNivelAcesso(), resumirFiliais(espaco.getId()));
        return new ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    @Transactional
    public ResultadoAutenticacao login(RequisicaoLogin requisicao, String userAgent) {
        Usuario usuario = usuarioRepository.findByEmail(requisicao.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(requisicao.getSenha(), usuario.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }

        String token = servicoJwt.gerarToken(usuario.getId(), usuario.getEspacoId(), usuario.getEmail(),
                usuario.isPrecisaTrocarSenha(), telefonePendente(usuario), usuario.getNivelAcesso(), usuario.getPapel());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(usuario.getId(), usuario.getEspacoId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), usuario.getEspacoId(), usuario.getPapel(), usuario.isPrecisaTrocarSenha(),
                telefonePendente(usuario), usuario.getNivelAcesso(), resumirFiliais(usuario.getEspacoId()));
        return new ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    @Transactional
    public ResultadoAutenticacao trocarSenha(RequisicaoTrocarSenha requisicao, String userAgent) {
        Usuario usuario = usuarioRepository.findById(contextoUsuario.usuarioAtual())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));

        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(requisicao.getSenhaAtual(), usuario.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha atual incorreta");
        }

        usuario.setSenhaHash(passwordEncoder.encode(requisicao.getNovaSenha()));
        usuario.setPrecisaTrocarSenha(false);
        usuarioRepository.save(usuario);

        String token = servicoJwt.gerarToken(usuario.getId(), usuario.getEspacoId(), usuario.getEmail(), false,
                telefonePendente(usuario), usuario.getNivelAcesso(), usuario.getPapel());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(usuario.getId(), usuario.getEspacoId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), usuario.getEspacoId(), usuario.getPapel(), false, telefonePendente(usuario),
                usuario.getNivelAcesso(), resumirFiliais(usuario.getEspacoId()));
        return new ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    @Transactional
    public ResultadoAutenticacao renovar(String tokenAtualizacaoBruto, String userAgent) {
        TokenRenovado renovado = servicoTokenAtualizacao.rotacionar(tokenAtualizacaoBruto, userAgent);

        Usuario usuario = usuarioRepository.findById(renovado.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));

        if (!renovado.espacoId().equals(usuario.getEspacoId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }

        String token = servicoJwt.gerarToken(usuario.getId(), usuario.getEspacoId(), usuario.getEmail(),
                usuario.isPrecisaTrocarSenha(), telefonePendente(usuario), usuario.getNivelAcesso(), usuario.getPapel());
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), usuario.getEspacoId(), usuario.getPapel(), usuario.isPrecisaTrocarSenha(),
                telefonePendente(usuario), usuario.getNivelAcesso(), resumirFiliais(usuario.getEspacoId()));
        return new ResultadoAutenticacao(resposta, renovado.tokenBruto());
    }

    /**
     * Grava o telefone do usuário autenticado e reemite o token — usado pela
     * tela de "cadastrar telefone" (gate de primeiro acesso, ver
     * {@code FiltroCadastroTelefoneObrigatorio}), tanto por quem se
     * autorregistrou sem telefone (legado, antes de virar campo obrigatório)
     * quanto por membros criados por um DONO sem telefone informado.
     */
    @Transactional
    public ResultadoAutenticacao cadastrarTelefone(String telefone, String userAgent) {
        Usuario usuario = usuarioRepository.findById(contextoUsuario.usuarioAtual())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));

        usuario.setTelefone(telefone);
        usuarioRepository.save(usuario);

        String token = servicoJwt.gerarToken(usuario.getId(), usuario.getEspacoId(), usuario.getEmail(),
                usuario.isPrecisaTrocarSenha(), false, usuario.getNivelAcesso(), usuario.getPapel());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(usuario.getId(), usuario.getEspacoId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), usuario.getEspacoId(), usuario.getPapel(), usuario.isPrecisaTrocarSenha(), false,
                usuario.getNivelAcesso(), resumirFiliais(usuario.getEspacoId()));
        return new ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    public void sair(String tokenAtualizacaoBruto) {
        if (tokenAtualizacaoBruto != null) {
            servicoTokenAtualizacao.revogar(tokenAtualizacaoBruto);
        }
    }

    private boolean telefonePendente(Usuario usuario) {
        return usuario.getTelefone() == null || usuario.getTelefone().isBlank();
    }

    private java.util.List<RespostaFilialResumo> resumirFiliais(Long espacoId) {
        return filialRepository.findByEspacoId(espacoId).stream()
                .map(e -> new RespostaFilialResumo(e.getId(), e.getNome(), e.getTipoPessoa()))
                .toList();
    }
}
