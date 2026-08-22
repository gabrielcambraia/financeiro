package com.financeiro.controller;

import com.financeiro.dto.RequisicaoAtualizarUsuario;
import com.financeiro.dto.RequisicaoCriarUsuario;
import com.financeiro.dto.RespostaUsuario;
import com.financeiro.dto.RespostaUsuarioCriado;
import com.financeiro.service.ServicoUsuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final ServicoUsuario servicoUsuario;

    @GetMapping
    public List<RespostaUsuario> listar() {
        return servicoUsuario.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespostaUsuarioCriado criar(@Valid @RequestBody RequisicaoCriarUsuario requisicao) {
        return servicoUsuario.criar(requisicao);
    }

    @PutMapping("/{id}")
    public RespostaUsuario atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoAtualizarUsuario requisicao) {
        return servicoUsuario.atualizar(id, requisicao);
    }
}
