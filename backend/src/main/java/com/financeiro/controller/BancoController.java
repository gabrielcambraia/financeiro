package com.financeiro.controller;

import com.financeiro.dto.BancoDTO;
import com.financeiro.entity.Banco;
import com.financeiro.service.BancoService;
import jakarta.validation.Valid;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GET (lista e logo) ficam abertos a qualquer usuário autenticado — todo
 * mundo escolhe banco ao cadastrar conta/cartão. A rota da logo também é
 * liberada de autenticação em ConfiguracaoWeb/ConfiguracaoSeguranca porque
 * uma tag {@code <img>} não envia o header Authorization. Escrita
 * (criar/editar/excluir/upload) é restrita a administradores.
 */
@RestController
@RequestMapping("/api/bancos")
@RequiredArgsConstructor
public class BancoController {

    private final BancoService service;

    @GetMapping
    public List<BancoDTO> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        Banco banco = service.buscarEntidade(id);
        if (banco.getLogo() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(banco.getLogoTipo()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .body(banco.getLogo());
    }

    @PostMapping
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    @ResponseStatus(HttpStatus.CREATED)
    public BancoDTO create(@Valid @RequestBody BancoDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public BancoDTO update(@PathVariable Long id, @Valid @RequestBody BancoDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/logo")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public BancoDTO uploadLogo(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) {
        try {
            return service.uploadLogo(id, arquivo.getBytes(), arquivo.getContentType());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao ler o arquivo enviado");
        }
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public BancoDTO removerLogo(@PathVariable Long id) {
        return service.removerLogo(id);
    }
}
