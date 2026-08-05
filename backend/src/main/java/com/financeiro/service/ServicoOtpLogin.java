package com.financeiro.service;

import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaEntidadeResumo;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.UsuarioEspaco;
import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.PropositoCodigo;
import com.financeiro.repository.EntidadeRepository;
import com.financeiro.repository.UsuarioEspacoRepository;
import com.financeiro.repository.UsuarioRepository;
import com.financeiro.seguranca.ServicoJwt;
import com.financeiro.seguranca.ServicoTokenAtualizacao;
import com.financeiro.seguranca.TokenRenovado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoOtpLogin {

    private final ServicoCodigoVerificacao servicoCodigoVerificacao;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioEspacoRepository usuarioEspacoRepository;
    private final EntidadeRepository entidadeRepository;
    private final ServicoJwt servicoJwt;
    private final ServicoTokenAtualizacao servicoTokenAtualizacao;

    @Transactional
    public void solicitarOtp(String email, String ipOrigem) {
        // Não revela se o e-mail existe — retorna silenciosamente em ambos os casos
        usuarioRepository.findByEmail(email).ifPresent(usuario ->
                servicoCodigoVerificacao.solicitar(email, CanalNotificacao.EMAIL, PropositoCodigo.LOGIN, ipOrigem, null));
    }

    @Transactional
    public ServicoAutenticacao.ResultadoAutenticacao verificarOtp(String email, String codigo, String userAgent) {
        servicoCodigoVerificacao.verificar(email, PropositoCodigo.LOGIN, codigo);

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        UsuarioEspaco vinculo = usuarioEspacoRepository.findByIdUsuarioId(usuario.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Usuário sem espaço vinculado"));

        String token = servicoJwt.gerarToken(usuario.getId(), vinculo.getId().getEspacoId(),
                usuario.getEmail(), usuario.isPrecisaTrocarSenha(), usuario.getNivelAcesso());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(
                usuario.getId(), vinculo.getId().getEspacoId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(
                token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                vinculo.getId().getEspacoId(), vinculo.getPapel(),
                usuario.isPrecisaTrocarSenha(), usuario.getNivelAcesso(),
                resumirEntidades(vinculo.getId().getEspacoId()));
        return new ServicoAutenticacao.ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    private List<RespostaEntidadeResumo> resumirEntidades(Long espacoId) {
        return entidadeRepository.findByEspacoId(espacoId).stream()
                .map(e -> new RespostaEntidadeResumo(e.getId(), e.getNome(), e.getTipoPessoa()))
                .toList();
    }
}
