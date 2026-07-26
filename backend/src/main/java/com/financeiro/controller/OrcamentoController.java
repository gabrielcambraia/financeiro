package com.financeiro.controller;

import com.financeiro.dto.OrcamentoDTO;
import com.financeiro.service.OrcamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orcamentos")
@RequiredArgsConstructor
public class OrcamentoController {

    private final OrcamentoService service;

    @GetMapping
    public List<OrcamentoDTO> findByMes(@RequestParam String mes) {
        return service.findByMes(mes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrcamentoDTO create(@Valid @RequestBody OrcamentoDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public OrcamentoDTO update(@PathVariable Long id, @Valid @RequestBody OrcamentoDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
