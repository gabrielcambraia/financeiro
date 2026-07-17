package com.financeiro.controller;

import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RequisicaoTrocarSenha;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.seguranca.AuxiliarCookieRenovacao;
import com.financeiro.service.ServicoAutenticacao;
import com.financeiro.service.ServicoAutenticacao.ResultadoAutenticacao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final ServicoAutenticacao servicoAutenticacao;
    private final AuxiliarCookieRenovacao auxiliarCookieRenovacao;

    public AutenticacaoController(ServicoAutenticacao servicoAutenticacao, AuxiliarCookieRenovacao auxiliarCookieRenovacao) {
        this.servicoAutenticacao = servicoAutenticacao;
        this.auxiliarCookieRenovacao = auxiliarCookieRenovacao;
    }

    @PostMapping("/register")
    public RespostaAutenticacao registrar(@Valid @RequestBody RequisicaoRegistro requisicao,
                                           HttpServletRequest request, HttpServletResponse response) {
        ResultadoAutenticacao resultado = servicoAutenticacao.registrar(requisicao, request.getHeader("User-Agent"));
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
}
