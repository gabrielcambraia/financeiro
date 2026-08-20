package com.financeiro.controller;

import com.financeiro.dto.FaturaDTO;
import com.financeiro.service.FaturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faturas")
@RequiredArgsConstructor
public class FaturaController {

    private final FaturaService service;

    @GetMapping
    public List<FaturaDTO> findAll(@RequestParam(required = false) Long cartaoId,
                                   @RequestParam(required = false) String month) {
        if (cartaoId != null) {
            return service.findByCartao(cartaoId, month);
        }
        return service.findAllByEspaco(month);
    }

    @GetMapping("/{id}")
    public FaturaDTO findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
