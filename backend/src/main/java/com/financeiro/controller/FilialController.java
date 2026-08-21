package com.financeiro.controller;

import com.financeiro.dto.RequisicaoCriarFilial;
import com.financeiro.dto.RespostaAssinatura;
import com.financeiro.dto.RespostaFilial;
import com.financeiro.service.ServicoFilial;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/filiais")
@RequiredArgsConstructor
public class FilialController {

    private final ServicoFilial servicoFilial;

    @GetMapping
    public List<RespostaFilial> listar() {
        return servicoFilial.listar();
    }

    @GetMapping("/{id}")
    public RespostaFilial buscar(@PathVariable Long id) {
        return servicoFilial.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespostaFilial criar(@Valid @RequestBody RequisicaoCriarFilial requisicao) {
        return servicoFilial.criar(requisicao);
    }

    @PutMapping("/{id}")
    public RespostaFilial atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoCriarFilial requisicao) {
        return servicoFilial.atualizar(id, requisicao);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        servicoFilial.excluir(id);
    }

    @GetMapping("/assinatura")
    public RespostaAssinatura assinatura() {
        return servicoFilial.resumoAssinatura();
    }
}
