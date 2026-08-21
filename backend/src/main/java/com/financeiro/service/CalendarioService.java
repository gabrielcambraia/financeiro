package com.financeiro.service;

import com.financeiro.context.ContextoFilial;
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
    private final ContextoFilial contextoFilial;

    public List<TransacaoDTO> buscarMes(String mes) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long filialId = contextoFilial.filialAtual();
        YearMonth ym = YearMonth.parse(mes);
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        var transacoes = filialId != null
                ? repository.findByEspacoIdAndDataVencimentoBetweenFiltradoPorFilial(espacoId, inicio, fim, filialId)
                : repository.findByEspacoIdAndDataVencimentoBetweenOrderByDataVencimentoAsc(espacoId, inicio, fim);

        return transacoes.stream()
                .filter(t -> t.getDataCancelamento() == null)
                .map(transacaoService::toDTO)
                .toList();
    }
}
