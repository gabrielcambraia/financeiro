package com.financeiro.controller;

import com.financeiro.dto.FluxoDeCaixaDTO;
import com.financeiro.service.FluxoDeCaixaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/fluxo-de-caixa")
@RequiredArgsConstructor
public class FluxoDeCaixaController {

    private final FluxoDeCaixaService service;

    @GetMapping
    public FluxoDeCaixaDTO projetar(
            @RequestParam(defaultValue = "90") int dias,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) BigDecimal simulacaoValor,
            @RequestParam(required = false) LocalDate simulacaoData) {
        return service.projetar(Math.min(365, Math.max(1, dias)), contaId, simulacaoValor, simulacaoData);
    }
}
