package com.financeiro.controller;

import com.financeiro.dto.RequisicaoAlterarModulosEspaco;
import com.financeiro.dto.RequisicaoAlterarTipoEspaco;
import com.financeiro.dto.RespostaEspacoAdmin;
import com.financeiro.dto.RespostaPaginada;
import com.financeiro.service.ServicoEspacoAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Tela de admin que lista todos os espaços (tenants) da plataforma, com
 * dono e membros vinculados, e permite reclassificar o tipo do espaço.
 * Restrito a administradores globais (ver {@code AutorizacaoAdmin}).
 */
@RestController
@RequestMapping("/api/admin/espacos")
@RequiredArgsConstructor
public class EspacoAdminController {

    private final ServicoEspacoAdmin servico;

    @GetMapping
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaPaginada<RespostaEspacoAdmin> listar(@RequestParam(defaultValue = "0") int pagina) {
        return servico.listar(pagina);
    }

    @PatchMapping("/{id}/tipo")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaEspacoAdmin alterarTipo(@PathVariable Long id,
                                            @Valid @RequestBody RequisicaoAlterarTipoEspaco requisicao) {
        return servico.alterarTipo(id, requisicao.getTipo());
    }

    @PatchMapping("/{id}/modulos")
    @PreAuthorize("@autorizacaoAdmin.exigirAdmin()")
    public RespostaEspacoAdmin alterarModulos(@PathVariable Long id,
                                               @Valid @RequestBody RequisicaoAlterarModulosEspaco requisicao) {
        return servico.alterarModulos(id, requisicao.getModulos());
    }
}
