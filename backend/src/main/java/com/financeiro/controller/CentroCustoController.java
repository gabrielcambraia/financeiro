package com.financeiro.controller;

import com.financeiro.dto.CentroCustoDTO;
import com.financeiro.service.CentroCustoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/centros-custo")
@RequiredArgsConstructor
public class CentroCustoController {

    private final CentroCustoService service;

    @GetMapping
    public List<CentroCustoDTO> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CentroCustoDTO create(@Valid @RequestBody CentroCustoDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public CentroCustoDTO update(@PathVariable Long id, @Valid @RequestBody CentroCustoDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
