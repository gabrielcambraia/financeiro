package com.financeiro.controller;

import com.financeiro.dto.RespostaConfiguracaoPlataforma;
import com.financeiro.entity.ConfiguracaoPlataforma;
import com.financeiro.service.ConfiguracaoPlataformaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * GET (info e logo) ficam abertos a qualquer um, sem autenticação — a logo
 * é usada como favicon do navegador (a página de login também precisa dela)
 * e uma tag {@code <img>}/{@code <link>} não envia o header Authorization.
 * Escrita (upload/remoção) é restrita a administradores.
 */
@RestController
@RequestMapping("/api/configuracao-plataforma")
@RequiredArgsConstructor
public class ConfiguracaoPlataformaController {

    private final ConfiguracaoPlataformaService service;

    @GetMapping
    public RespostaConfiguracaoPlataforma obter() {
        return service.obterInfo();
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        ConfiguracaoPlataforma configuracao = service.buscarEntidade();
        if (configuracao.getLogo() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(configuracao.getLogoTipo()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .body(configuracao.getLogo());
    }

    @PostMapping("/logo")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaConfiguracaoPlataforma uploadLogo(@RequestParam("arquivo") MultipartFile arquivo) {
        try {
            return service.uploadLogo(arquivo.getBytes(), arquivo.getContentType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao ler o arquivo enviado");
        }
    }

    @DeleteMapping("/logo")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaConfiguracaoPlataforma removerLogo() {
        return service.removerLogo();
    }

    // Banner exibido na tela de login — endpoints espelham os de /logo acima,
    // mas gravam no slot logoLogin (independente do ícone da barra lateral/favicon).
    @GetMapping("/logo-login")
    public ResponseEntity<byte[]> logoLogin() {
        ConfiguracaoPlataforma configuracao = service.buscarEntidade();
        if (configuracao.getLogoLogin() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(configuracao.getLogoLoginTipo()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .body(configuracao.getLogoLogin());
    }

    @PostMapping("/logo-login")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaConfiguracaoPlataforma uploadLogoLogin(@RequestParam("arquivo") MultipartFile arquivo) {
        try {
            return service.uploadLogoLogin(arquivo.getBytes(), arquivo.getContentType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao ler o arquivo enviado");
        }
    }

    @DeleteMapping("/logo-login")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaConfiguracaoPlataforma removerLogoLogin() {
        return service.removerLogoLogin();
    }
}
