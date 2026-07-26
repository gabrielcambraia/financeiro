package com.financeiro.controller;

import com.financeiro.dto.TransacaoDTO;
import com.financeiro.service.CalendarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calendario")
@RequiredArgsConstructor
public class CalendarioController {

    private final CalendarioService service;

    @GetMapping
    public List<TransacaoDTO> buscarMes(@RequestParam String mes) {
        return service.buscarMes(mes);
    }
}
