package com.financeiro.controller;

import com.financeiro.dto.CartaoDTO;
import com.financeiro.service.CartaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cartoes")
@RequiredArgsConstructor
public class CartaoController {

    private final CartaoService service;

    @GetMapping
    public List<CartaoDTO> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartaoDTO create(@Valid @RequestBody CartaoDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public CartaoDTO update(@PathVariable Long id, @Valid @RequestBody CartaoDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
