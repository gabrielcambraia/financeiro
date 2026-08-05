package com.financeiro.controller;

import com.financeiro.dto.RequisicaoAtualizarPlano;
import com.financeiro.dto.RespostaPlano;
import com.financeiro.entity.Plano;
import com.financeiro.service.ServicoPlano;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/planos")
@RequiredArgsConstructor
public class AdminPlanoController {

    private final ServicoPlano servicoPlano;

    @GetMapping
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public List<RespostaPlano> listar() {
        return servicoPlano.listar().stream()
                .map(this::mapear)
                .toList();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaPlano atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoAtualizarPlano requisicao) {
        if (requisicao.getLimiteEntidades() != null) {
            servicoPlano.atualizarLimite(id, requisicao.getLimiteEntidades());
        }
        if (requisicao.getAtivo() != null) {
            servicoPlano.alterarAtivo(id, requisicao.getAtivo());
        }
        Plano plano = servicoPlano.listar().stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow();
        return mapear(plano);
    }

    private RespostaPlano mapear(Plano p) {
        return new RespostaPlano(p.getId(), p.getCodigo(), p.getNome(), p.getLimiteEntidades(), p.isAtivo(), p.getCriadoEm());
    }
}
