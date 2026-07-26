package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Somente leitura: agenda por dataVencimento no mês. Faturas de cartão e
 * parcelas de dívida já são Transacao normais (ver AgendadorFatura e
 * DividaService), então aparecem aqui de graça, sem consulta extra.
 */
@Service
@RequiredArgsConstructor
public class CalendarioService {

    private final TransacaoRepository repository;
    private final TransacaoService transacaoService;
    private final ContextoEspaco contextoEspaco;

    public List<TransacaoDTO> buscarMes(String mes) {
        Long espacoId = contextoEspaco.espacoAtual();
        YearMonth ym = YearMonth.parse(mes);
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        return repository.findByEspacoIdAndDataVencimentoBetweenOrderByDataVencimentoAsc(espacoId, inicio, fim).stream()
                .filter(t -> t.getDataCancelamento() == null)
                .map(transacaoService::toDTO)
                .toList();
    }
}
