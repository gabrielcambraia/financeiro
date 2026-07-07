package com.financeiro.controller;

import com.financeiro.dto.ContaDTO;
import com.financeiro.service.ContaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contas")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService service;

    @GetMapping
    public List<ContaDTO> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaDTO create(@Valid @RequestBody ContaDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ContaDTO update(@PathVariable Long id, @Valid @RequestBody ContaDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
