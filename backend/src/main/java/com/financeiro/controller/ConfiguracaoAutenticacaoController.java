package com.financeiro.controller;

import com.financeiro.dto.RespostaConfigAuth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O frontend chama isso no boot para saber se deve exigir login — a
 * autenticação é sempre obrigatória.
 */
@RestController
@RequestMapping("/api/auth")
public class ConfiguracaoAutenticacaoController {

    @GetMapping("/config")
    public RespostaConfigAuth config() {
        return new RespostaConfigAuth(true);
    }
}
