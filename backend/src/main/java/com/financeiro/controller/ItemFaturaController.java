package com.financeiro.controller;

import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.service.ItemFaturaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itens-fatura")
@RequiredArgsConstructor
public class ItemFaturaController {

    private final ItemFaturaService service;

    @GetMapping
    public List<ItemFaturaDTO> findAll(
            @RequestParam Long cartaoId,
            @RequestParam(required = false) String month) {
        return service.findByFilters(cartaoId, month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ItemFaturaDTO> create(@Valid @RequestBody ItemFaturaDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ItemFaturaDTO update(@PathVariable Long id, @Valid @RequestBody ItemFaturaDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/cancelar")
    public ItemFaturaDTO cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }
}
