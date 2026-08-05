package com.financeiro.controller;

import com.financeiro.dto.RequisicaoConfirmarCodigo;
import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RequisicaoSolicitarOtp;
import com.financeiro.dto.RequisicaoTrocarSenha;
import com.financeiro.dto.RequisicaoVerificarOtp;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.seguranca.AuxiliarCookieRenovacao;
import com.financeiro.service.ServicoAutenticacao;
import com.financeiro.service.ServicoAutenticacao.ResultadoAutenticacao;
import com.financeiro.service.ServicoOtpLogin;
import com.financeiro.service.ServicoVerificacaoContato;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AutenticacaoController {

    private final ServicoAutenticacao servicoAutenticacao;
    private final ServicoOtpLogin servicoOtpLogin;
    private final ServicoVerificacaoContato servicoVerificacaoContato;
    private final AuxiliarCookieRenovacao auxiliarCookieRenovacao;

    @PostMapping("/register")
    public RespostaAutenticacao registrar(@Valid @RequestBody RequisicaoRegistro requisicao,
                                           HttpServletRequest request, HttpServletResponse response) {
        ResultadoAutenticacao resultado = servicoAutenticacao.registrar(
                requisicao, request.getHeader("User-Agent"), request.getRemoteAddr());
        auxiliarCookieRenovacao.escrever(response, resultado.tokenAtualizacaoBruto());
        return resultado.resposta();
    }

    @PostMapping("/login")
    public RespostaAutenticacao login(@Valid @RequestBody RequisicaoLogin requisicao,
                                       HttpServletRequest request, HttpServletResponse response) {
        ResultadoAutenticacao resultado = servicoAutenticacao.login(requisicao, request.getHeader("User-Agent"));
        auxiliarCookieRenovacao.escrever(response, resultado.tokenAtualizacaoBruto());
        return resultado.resposta();
    }

    @PostMapping("/trocar-senha")
    public RespostaAutenticacao trocarSenha(@Valid @RequestBody RequisicaoTrocarSenha requisicao,
                                             HttpServletRequest request, HttpServletResponse response) {
        ResultadoAutenticacao resultado = servicoAutenticacao.trocarSenha(requisicao, request.getHeader("User-Agent"));
        auxiliarCookieRenovacao.escrever(response, resultado.tokenAtualizacaoBruto());
        return resultado.resposta();
    }

    @PostMapping("/renovar")
    public RespostaAutenticacao renovar(HttpServletRequest request, HttpServletResponse response) {
        String tokenAtualizacaoBruto = auxiliarCookieRenovacao.ler(request);
        if (tokenAtualizacaoBruto == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }
        ResultadoAutenticacao resultado = servicoAutenticacao.renovar(tokenAtualizacaoBruto, request.getHeader("User-Agent"));
        auxiliarCookieRenovacao.escrever(response, resultado.tokenAtualizacaoBruto());
        return resultado.resposta();
    }

    @PostMapping("/sair")
    public void sair(HttpServletRequest request, HttpServletResponse response) {
        servicoAutenticacao.sair(auxiliarCookieRenovacao.ler(request));
        auxiliarCookieRenovacao.limpar(response);
    }

    // --- OTP login ---

    @PostMapping("/otp/solicitar")
    public void solicitarOtp(@Valid @RequestBody RequisicaoSolicitarOtp requisicao, HttpServletRequest request) {
        servicoOtpLogin.solicitarOtp(requisicao.getEmail(), request.getRemoteAddr());
    }

    @PostMapping("/otp/verificar")
    public RespostaAutenticacao verificarOtp(@Valid @RequestBody RequisicaoVerificarOtp requisicao,
                                              HttpServletRequest request, HttpServletResponse response) {
        ResultadoAutenticacao resultado = servicoOtpLogin.verificarOtp(
                requisicao.getEmail(), requisicao.getCodigo(), request.getHeader("User-Agent"));
        auxiliarCookieRenovacao.escrever(response, resultado.tokenAtualizacaoBruto());
        return resultado.resposta();
    }

    // --- Verificação de contato (autenticado) ---

    @PostMapping("/verificar-contato/email/solicitar")
    public void solicitarVerificacaoEmail(HttpServletRequest request) {
        servicoVerificacaoContato.solicitarVerificacaoEmail(request.getRemoteAddr());
    }

    @PostMapping("/verificar-contato/email/confirmar")
    public void confirmarVerificacaoEmail(@Valid @RequestBody RequisicaoConfirmarCodigo requisicao) {
        servicoVerificacaoContato.confirmarVerificacaoEmail(requisicao.getCodigo());
    }

    @PostMapping("/verificar-contato/telefone/solicitar")
    public void solicitarVerificacaoTelefone(HttpServletRequest request) {
        servicoVerificacaoContato.solicitarVerificacaoTelefone(request.getRemoteAddr());
    }

    @PostMapping("/verificar-contato/telefone/confirmar")
    public void confirmarVerificacaoTelefone(@Valid @RequestBody RequisicaoConfirmarCodigo requisicao) {
        servicoVerificacaoContato.confirmarVerificacaoTelefone(requisicao.getCodigo());
    }

}
