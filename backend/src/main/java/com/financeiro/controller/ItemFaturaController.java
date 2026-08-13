package com.financeiro.controller;

import com.financeiro.dto.ConversaoParaDebitoDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.service.ConversaoLancamentoService;
import com.financeiro.service.ItemFaturaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/itens-fatura")
@RequiredArgsConstructor
public class ItemFaturaController {

    private final ItemFaturaService service;
    private final ConversaoLancamentoService conversaoService;

    // Usado pela tela de Lançamentos: itens em aberto filtrados por MÊS DE
    // COMPETÊNCIA (calendário), opcionalmente por cartão e/ou pela conta de
    // pagamento do cartão. Não confundir com /ciclo abaixo.
    @GetMapping
    public List<ItemFaturaDTO> findAll(
            @RequestParam(required = false) Long cartaoId,
            @RequestParam(required = false) Long contaId,
            @RequestParam(required = false) String month) {
        return service.buscarAbertosPorFiltro(month, contaId, cartaoId);
    }

    // Usado pela tela do cartão: itens em aberto do CICLO DE FATURAMENTO que
    // fecha no mês informado (baseado no dia de fechamento do cartão, não no
    // mês corrido da data da compra) — permite navegar mês a mês vendo
    // exatamente o que vai entrar em cada fatura futura.
    @GetMapping("/ciclo")
    public List<ItemFaturaDTO> findAbertosPorCiclo(
            @RequestParam Long cartaoId,
            @RequestParam(required = false) String month) {
        return service.findAbertosPorCiclo(cartaoId, month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ItemFaturaDTO> create(@Valid @RequestBody ItemFaturaDTO dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public ItemFaturaDTO update(@PathVariable Long id, @Valid @RequestBody ItemFaturaDTO dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PatchMapping("/{id}/cancelar")
    public ItemFaturaDTO cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    @PostMapping("/{id}/converter-para-debito")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransacaoDTO> converterParaDebito(
            @PathVariable Long id,
            @Valid @RequestBody ConversaoParaDebitoDTO dto) {
        return conversaoService.converterParaDebito(id, dto);
    }
}
