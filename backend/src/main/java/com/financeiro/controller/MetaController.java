package com.financeiro.controller;

import com.financeiro.dto.MetaDTO;
import com.financeiro.dto.MetaMovimentoDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.service.MetaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metas")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService service;

    @GetMapping
    public List<MetaDTO> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetaDTO create(@Valid @RequestBody MetaDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public MetaDTO update(@PathVariable Long id, @Valid @RequestBody MetaDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/cancelar")
    public MetaDTO cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    @PatchMapping("/{id}/aportar")
    public MetaDTO aportar(@PathVariable Long id, @Valid @RequestBody MetaMovimentoDTO dto) {
        return service.aportar(id, dto);
    }

    @PatchMapping("/{id}/resgatar")
    public MetaDTO resgatar(@PathVariable Long id, @Valid @RequestBody MetaMovimentoDTO dto) {
        return service.resgatar(id, dto);
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
