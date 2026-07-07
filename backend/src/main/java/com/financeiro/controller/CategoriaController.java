package com.financeiro.controller;

import com.financeiro.dto.CategoriaDTO;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService service;

    @GetMapping
    public List<CategoriaDTO> findAll(@RequestParam(required = false) TipoTransacao tipo) {
        return service.findAll(tipo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaDTO create(@Valid @RequestBody CategoriaDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public CategoriaDTO update(@PathVariable Long id, @Valid @RequestBody CategoriaDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
