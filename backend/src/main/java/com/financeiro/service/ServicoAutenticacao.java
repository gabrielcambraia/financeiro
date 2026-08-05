package com.financeiro.service;

import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.PrimeiraEntidadeDTO;
import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RequisicaoTrocarSenha;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaEntidadeResumo;
import com.financeiro.entity.Entidade;
import com.financeiro.entity.Espaco;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.UsuarioEspacoId;
import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.CodigoPlano;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.entity.enums.PlanoEspaco;
import com.financeiro.entity.enums.PropositoCodigo;
import com.financeiro.entity.enums.TipoEspaco;
import com.financeiro.repository.EntidadeRepository;
import com.financeiro.repository.EspacoRepository;
import com.financeiro.repository.UsuarioEspacoRepository;
import com.financeiro.repository.UsuarioRepository;
import com.financeiro.seguranca.ServicoJwt;
import com.financeiro.seguranca.ServicoTokenAtualizacao;
import com.financeiro.seguranca.TokenRenovado;
import org.springframework.security.authentication.BadCredentialsException;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class ServicoAutenticacao {

    private final UsuarioRepository usuarioRepository;
    private final EspacoRepository espacoRepository;
    private final UsuarioEspacoRepository usuarioEspacoRepository;
    private final EntidadeRepository entidadeRepository;
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
            UsuarioEspacoRepository usuarioEspacoRepository,
            EntidadeRepository entidadeRepository,
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
        this.usuarioEspacoRepository = usuarioEspacoRepository;
        this.entidadeRepository = entidadeRepository;
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

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nome(requisicao.getNome())
                .email(requisicao.getEmail())
                .senhaHash(passwordEncoder.encode(requisicao.getSenha()))
                .telefone(requisicao.getTelefone())
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

        servicoAssinatura.criarParaEspaco(espaco.getId(), CodigoPlano.INDIVIDUAL);

        PrimeiraEntidadeDTO ent = requisicao.getEntidade();
        String docLimpo = validadorDocumento.limparEValidar(ent.getDocumento(), ent.getTipoPessoa());
        String hashDoc = cifradorDados.hashDocumento(docLimpo);
        entidadeRepository.save(Entidade.builder()
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

        // Envio de código de verificação de e-mail (assíncrono — não bloqueia a transação)
        try {
            servicoCodigoVerificacao.solicitar(
                    usuario.getEmail(), CanalNotificacao.EMAIL, PropositoCodigo.VERIFICAR_EMAIL, ipOrigem, usuario.getId());
        } catch (ResponseStatusException e) {
            log.warn("Envio de código de verificação falhou no registro: {}", e.getReason());
        }

        String token = servicoJwt.gerarToken(usuario.getId(), espaco.getId(), usuario.getEmail(), false, usuario.getNivelAcesso());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(usuario.getId(), espaco.getId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), espaco.getId(), PapelUsuario.DONO, false, usuario.getNivelAcesso(),
                resumirEntidades(espaco.getId()));
        return new ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    @Transactional
    public ResultadoAutenticacao login(RequisicaoLogin requisicao, String userAgent) {
        Usuario usuario = usuarioRepository.findByEmail(requisicao.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(requisicao.getSenha(), usuario.getSenhaHash())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        UsuarioEspaco vinculo = usuarioEspacoRepository.findByIdUsuarioId(usuario.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Usuário sem espaço vinculado"));

        String token = servicoJwt.gerarToken(usuario.getId(), vinculo.getId().getEspacoId(), usuario.getEmail(),
                usuario.isPrecisaTrocarSenha(), usuario.getNivelAcesso());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(
                usuario.getId(), vinculo.getId().getEspacoId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), vinculo.getId().getEspacoId(), vinculo.getPapel(), usuario.isPrecisaTrocarSenha(),
                usuario.getNivelAcesso(), resumirEntidades(vinculo.getId().getEspacoId()));
        return new ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    @Transactional
    public ResultadoAutenticacao trocarSenha(RequisicaoTrocarSenha requisicao, String userAgent) {
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

        String token = servicoJwt.gerarToken(usuario.getId(), vinculo.getId().getEspacoId(), usuario.getEmail(), false,
                usuario.getNivelAcesso());
        // Revoga tokens de outros dispositivos: uma troca de senha (inclusive por
        // suspeita de comprometimento) não deve deixar sessões antigas vivas.
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(
                usuario.getId(), vinculo.getId().getEspacoId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), vinculo.getId().getEspacoId(), vinculo.getPapel(), false, usuario.getNivelAcesso(),
                resumirEntidades(vinculo.getId().getEspacoId()));
        return new ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    @Transactional
    public ResultadoAutenticacao renovar(String tokenAtualizacaoBruto, String userAgent) {
        TokenRenovado renovado = servicoTokenAtualizacao.rotacionar(tokenAtualizacaoBruto, userAgent);

        Usuario usuario = usuarioRepository.findById(renovado.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));
        UsuarioEspaco vinculo = usuarioEspacoRepository.findByIdUsuarioId(usuario.getId()).stream()
                .filter(v -> v.getId().getEspacoId().equals(renovado.espacoId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));

        String token = servicoJwt.gerarToken(usuario.getId(), vinculo.getId().getEspacoId(), usuario.getEmail(),
                usuario.isPrecisaTrocarSenha(), usuario.getNivelAcesso());
        RespostaAutenticacao resposta = new RespostaAutenticacao(token, usuario.getId(), usuario.getNome(),
                usuario.getEmail(), vinculo.getId().getEspacoId(), vinculo.getPapel(), usuario.isPrecisaTrocarSenha(),
                usuario.getNivelAcesso(), resumirEntidades(vinculo.getId().getEspacoId()));
        return new ResultadoAutenticacao(resposta, renovado.tokenBruto());
    }

    public void sair(String tokenAtualizacaoBruto) {
        if (tokenAtualizacaoBruto != null) {
            servicoTokenAtualizacao.revogar(tokenAtualizacaoBruto);
        }
    }

    private java.util.List<RespostaEntidadeResumo> resumirEntidades(Long espacoId) {
        return entidadeRepository.findByEspacoId(espacoId).stream()
                .map(e -> new RespostaEntidadeResumo(e.getId(), e.getNome(), e.getTipoPessoa()))
                .toList();
    }
}
