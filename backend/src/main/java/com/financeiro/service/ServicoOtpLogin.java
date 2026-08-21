package com.financeiro.service;

import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaFilialResumo;
import com.financeiro.entity.Usuario;
import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.PropositoCodigo;
import com.financeiro.repository.FilialRepository;
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
    private final FilialRepository filialRepository;
    private final ServicoJwt servicoJwt;
    private final ServicoTokenAtualizacao servicoTokenAtualizacao;

    @Transactional
    public void solicitarOtp(String email, String ipOrigem) {
        usuarioRepository.findByEmail(email).ifPresent(usuario ->
                servicoCodigoVerificacao.solicitar(email, CanalNotificacao.EMAIL, PropositoCodigo.LOGIN, ipOrigem, null));
    }

    @Transactional
    public ServicoAutenticacao.ResultadoAutenticacao verificarOtp(String email, String codigo, String userAgent) {
        servicoCodigoVerificacao.verificar(email, PropositoCodigo.LOGIN, codigo);

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        String token = servicoJwt.gerarToken(usuario.getId(), usuario.getEspacoId(),
                usuario.getEmail(), usuario.isPrecisaTrocarSenha(), usuario.getNivelAcesso(), usuario.getPapel());
        TokenRenovado tokenAtualizacao = servicoTokenAtualizacao.emitir(
                usuario.getId(), usuario.getEspacoId(), userAgent);
        RespostaAutenticacao resposta = new RespostaAutenticacao(
                token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getEspacoId(), usuario.getPapel(),
                usuario.isPrecisaTrocarSenha(), usuario.getNivelAcesso(),
                resumirFiliais(usuario.getEspacoId()));
        return new ServicoAutenticacao.ResultadoAutenticacao(resposta, tokenAtualizacao.tokenBruto());
    }

    private List<RespostaFilialResumo> resumirFiliais(Long espacoId) {
        return filialRepository.findByEspacoId(espacoId).stream()
                .map(e -> new RespostaFilialResumo(e.getId(), e.getNome(), e.getTipoPessoa()))
                .toList();
    }
}
