package com.financeiro.controller;

import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.service.TransacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transacoes")
@RequiredArgsConstructor
public class TransacaoController {

    private final TransacaoService service;

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
    public TransacaoDTO update(@PathVariable Long id, @Valid @RequestBody TransacaoDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "UNICA") String scope) {
        service.delete(id, scope);
    }

    @PatchMapping("/{id}/pagar")
    public TransacaoDTO pagar(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        LocalDate dataPagamento = body != null && body.get("dataPagamento") != null
                ? LocalDate.parse(body.get("dataPagamento"))
                : null;
        BigDecimal multa = body != null && body.get("multa") != null
                ? new BigDecimal(body.get("multa"))
                : null;
        return service.pagar(id, dataPagamento, multa);
    }

    @PatchMapping("/{id}/estornar")
    public TransacaoDTO estornar(@PathVariable Long id) {
        return service.estornar(id);
    }

    @PatchMapping("/{id}/cancelar")
    public TransacaoDTO cancelar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "UNICA") String scope) {
        return service.cancelar(id, scope);
    }
}
