package com.financeiro.controller;

import com.financeiro.dto.PainelDTO;
import com.financeiro.service.PainelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/painel")
@RequiredArgsConstructor
public class PainelController {

    private final PainelService service;

    @GetMapping
    public PainelDTO getDashboard(
            @RequestParam String month,
            @RequestParam(required = false) Long contaId) {
        return service.getDashboard(month, contaId);
    }
}
