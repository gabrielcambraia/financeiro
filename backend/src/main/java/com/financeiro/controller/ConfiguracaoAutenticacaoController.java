package com.financeiro.controller;

import com.financeiro.dto.RespostaConfigAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Disponível em qualquer perfil: o frontend chama isso no boot para saber
 * se deve exigir login (perfil "nuvem") ou pular direto pro app (desktop-local).
 */
@RestController
@RequestMapping("/api/auth")
public class ConfiguracaoAutenticacaoController {

    @Value("${spring.profiles.active:}")
    private String perfisAtivos;

    @GetMapping("/config")
    public RespostaConfigAuth config() {
        boolean requerAutenticacao = List.of(perfisAtivos.split(",")).contains("nuvem");
        return new RespostaConfigAuth(requerAutenticacao);
    }
}
