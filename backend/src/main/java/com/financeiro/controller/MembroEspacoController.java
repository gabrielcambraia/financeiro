package com.financeiro.controller;

import com.financeiro.dto.RequisicaoAdicionarMembro;
import com.financeiro.dto.RespostaMembroAdicionado;
import com.financeiro.service.ServicoMembroEspaco;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/espacos/membros")
public class MembroEspacoController {

    private final ServicoMembroEspaco servicoMembroEspaco;

    public MembroEspacoController(ServicoMembroEspaco servicoMembroEspaco) {
        this.servicoMembroEspaco = servicoMembroEspaco;
    }

    @PostMapping
    public RespostaMembroAdicionado adicionar(@Valid @RequestBody RequisicaoAdicionarMembro requisicao) {
        return servicoMembroEspaco.adicionar(requisicao);
    }
}
