package com.financeiro.controller;

import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.service.TransacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transacoes")
@RequiredArgsConstructor
public class TransacaoController {

    private final TransacaoService service;

    @GetMapping
    public List<TransacaoDTO> findAll(
            @RequestParam String month,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) TipoTransacao tipo,
            @RequestParam(required = false) Long categoriaId) {
        return service.findByFilters(month, contaId, tipo, categoriaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransacaoDTO> create(@Valid @RequestBody TransacaoDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public TransacaoDTO update(@PathVariable Long id, @Valid @RequestBody TransacaoDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "UNICA") String scope) {
        service.delete(id, scope);
    }
}
