package com.financeiro.controller;

import com.financeiro.dto.RecorrenciaDTO;
import com.financeiro.service.RecorrenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recorrencias")
@RequiredArgsConstructor
public class RecorrenciaController {

    private final RecorrenciaService service;

    @GetMapping
    public List<RecorrenciaDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public RecorrenciaDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecorrenciaDTO criar(@RequestBody @Valid RecorrenciaDTO dto) {
        return service.criar(dto);
    }

    @PutMapping("/{id}")
    public RecorrenciaDTO atualizar(@PathVariable Long id, @RequestBody @Valid RecorrenciaDTO dto) {
        return service.atualizar(id, dto);
    }

    @PatchMapping("/{id}/ativa")
    public RecorrenciaDTO alternarAtiva(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean ativa = Boolean.TRUE.equals(body.get("ativa"));
        return service.alternarAtiva(id, ativa);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }

    @PostMapping("/{id}/gerar-mes-atual")
    public RecorrenciaDTO gerarMesAtual(@PathVariable Long id) {
        return service.gerarMesAtual(id);
    }
}
