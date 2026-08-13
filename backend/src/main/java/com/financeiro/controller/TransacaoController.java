package com.financeiro.controller;

import com.financeiro.dto.ConversaoParaCartaoDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.EscopoAtualizacao;
import com.financeiro.entity.enums.EscopoExclusao;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.service.ConversaoLancamentoService;
import com.financeiro.service.TransacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transacoes")
@RequiredArgsConstructor
public class TransacaoController {

    private final TransacaoService service;
    private final ConversaoLancamentoService conversaoService;

    @GetMapping
    public List<TransacaoDTO> findAll(
            @RequestParam String month,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) TipoTransacao tipo,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) LocalDate dataVencimentoInicio,
            @RequestParam(required = false) LocalDate dataVencimentoFim) {
        return service.findByFilters(month, contaId, tipo, categoriaId, dataVencimentoInicio, dataVencimentoFim);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransacaoDTO> create(@Valid @RequestBody TransacaoDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public TransacaoDTO update(
            @PathVariable Long id,
            @RequestParam(defaultValue = "UNICA") EscopoAtualizacao scope,
            @Valid @RequestBody TransacaoDTO dto) {
        return service.update(id, dto, scope);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "UNICA") EscopoExclusao scope) {
        service.delete(id, scope);
    }

    @PatchMapping("/{id}/pagar")
    public TransacaoDTO pagar(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String dataPagamentoStr = body != null ? body.get("dataPagamento") : null;
        String multaStr = body != null ? body.get("multa") : null;
        return service.pagar(id, dataPagamentoStr, multaStr);
    }

    @PatchMapping("/{id}/estornar")
    public TransacaoDTO estornar(@PathVariable Long id) {
        return service.estornar(id);
    }

    @PatchMapping("/{id}/cancelar")
    public TransacaoDTO cancelar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "UNICA") EscopoExclusao scope) {
        return service.cancelar(id, scope);
    }

    @GetMapping("/{id}/impacto-cancelamento")
    public RespostaImpacto impactoCancelamento(@PathVariable Long id) {
        return service.calcularImpactoCancelamento(id);
    }

    @GetMapping("/{id}/impacto-exclusao")
    public RespostaImpacto impactoExclusao(@PathVariable Long id) {
        return service.calcularImpactoExclusao(id);
    }

    @PostMapping("/{id}/converter-para-cartao")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ItemFaturaDTO> converterParaCartao(
            @PathVariable Long id,
            @Valid @RequestBody ConversaoParaCartaoDTO dto) {
        return conversaoService.converterParaCredito(id, dto);
    }
}
