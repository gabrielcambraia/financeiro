package com.financeiro.controller;

import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RequisicaoTrocarSenha;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.service.ServicoAutenticacao;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * Só existe no perfil "nuvem" — no modo desktop-local não há
 * {@link ServicoAutenticacao} (nem sentido em registrar/logar).
 */
@RestController
@RequestMapping("/api/auth")
@Profile("nuvem")
public class AutenticacaoController {

    private final ServicoAutenticacao servicoAutenticacao;

    public AutenticacaoController(ServicoAutenticacao servicoAutenticacao) {
        this.servicoAutenticacao = servicoAutenticacao;
    }

    @PostMapping("/register")
    public RespostaAutenticacao registrar(@Valid @RequestBody RequisicaoRegistro requisicao) {
        return servicoAutenticacao.registrar(requisicao);
    }

    @PostMapping("/login")
    public RespostaAutenticacao login(@Valid @RequestBody RequisicaoLogin requisicao) {
        return servicoAutenticacao.login(requisicao);
    }

    @PostMapping("/trocar-senha")
    public RespostaAutenticacao trocarSenha(@Valid @RequestBody RequisicaoTrocarSenha requisicao) {
        return servicoAutenticacao.trocarSenha(requisicao);
    }
}
