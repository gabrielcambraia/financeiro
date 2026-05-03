package com.financeiro.controller;

import com.financeiro.dto.TransactionDTO;
import com.financeiro.entity.enums.TransactionType;
import com.financeiro.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    @GetMapping
    public List<TransactionDTO> findAll(
            @RequestParam String month,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId) {
        return service.findByFilters(month, accountId, type, categoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionDTO> create(@Valid @RequestBody TransactionDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public TransactionDTO update(@PathVariable Long id, @Valid @RequestBody TransactionDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "SINGLE") String scope) {
        service.delete(id, scope);
    }
}
