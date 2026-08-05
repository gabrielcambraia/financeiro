package com.financeiro.controller;

import com.financeiro.dto.RequisicaoCriarEntidade;
import com.financeiro.dto.RespostaAssinatura;
import com.financeiro.dto.RespostaEntidade;
import com.financeiro.service.ServicoEntidade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/entidades")
@RequiredArgsConstructor
public class EntidadeController {

    private final ServicoEntidade servicoEntidade;

    @GetMapping
    public List<RespostaEntidade> listar() {
        return servicoEntidade.listar();
    }

    @GetMapping("/{id}")
    public RespostaEntidade buscar(@PathVariable Long id) {
        return servicoEntidade.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespostaEntidade criar(@Valid @RequestBody RequisicaoCriarEntidade requisicao) {
        return servicoEntidade.criar(requisicao);
    }

    @PutMapping("/{id}")
    public RespostaEntidade atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoCriarEntidade requisicao) {
        return servicoEntidade.atualizar(id, requisicao);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        servicoEntidade.excluir(id);
    }

    @GetMapping("/assinatura")
    public RespostaAssinatura assinatura() {
        return servicoEntidade.resumoAssinatura();
    }
}
