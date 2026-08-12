package com.financeiro.controller;

import com.financeiro.dto.RespostaConsultaCep;
import com.financeiro.dto.RespostaConsultaCnpj;
import com.financeiro.service.ServicoConsultaExterna;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consultas")
@RequiredArgsConstructor
public class ConsultaExternaController {

    private final ServicoConsultaExterna servicoConsultaExterna;

    @GetMapping("/cnpj/{cnpj}")
    public RespostaConsultaCnpj cnpj(@PathVariable String cnpj) {
        return servicoConsultaExterna.consultarCnpj(cnpj);
    }

    @GetMapping("/cep/{cep}")
    public RespostaConsultaCep cep(@PathVariable String cep) {
        return servicoConsultaExterna.consultarCep(cep);
    }
}
