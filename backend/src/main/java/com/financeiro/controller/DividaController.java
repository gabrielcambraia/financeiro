package com.financeiro.controller;

import com.financeiro.dto.DividaDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.service.DividaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dividas")
@RequiredArgsConstructor
public class DividaController {

    private final DividaService service;

    @GetMapping
    public List<DividaDTO> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}/parcelas")
    public List<TransacaoDTO> parcelas(@PathVariable Long id) {
        return service.parcelas(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DividaDTO create(@Valid @RequestBody DividaDTO dto) {
        return service.create(dto);
    }

    @PatchMapping("/{id}/cancelar")
    public DividaDTO cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/impacto-cancelamento")
    public RespostaImpacto impactoCancelamento(@PathVariable Long id) {
        return service.calcularImpactoCancelamento(id);
    }

    @GetMapping("/{id}/impacto-exclusao")
    public RespostaImpacto impactoExclusao(@PathVariable Long id) {
        return service.calcularImpactoExclusao(id);
    }
}
