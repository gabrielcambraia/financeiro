package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.ProjecaoDTO;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.DirecaoTransferencia;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Projeta o saldo dia a dia assumindo que cada pendência (PENDENTE/ATRASADA)
 * é paga exatamente no seu vencimento — é uma simulação, não uma promessa:
 * quitação continua manual (ver TransacaoService), então o saldo real só se
 * move quando alguém de fato marcar como pago.
 */
@Service
@RequiredArgsConstructor
public class ProjecaoService {

    private final ContaRepository contaRepository;
    private final TransacaoRepository transacaoRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoEntidade contextoEntidade;

    public ProjecaoDTO projetar(int dias, Long contaId, BigDecimal simulacaoValor, LocalDate simulacaoData) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long entidadeId = contextoEntidade.entidadeAtual();
        LocalDate hoje = LocalDate.now();
        LocalDate fim = hoje.plusDays(dias);

        BigDecimal saldoAtual = contaId != null
                ? contaRepository.findByIdAndEspacoId(contaId, espacoId).map(Conta::getSaldo).orElse(BigDecimal.ZERO)
                : (entidadeId != null
                        ? contaRepository.findByEspacoIdFiltradoPorEntidade(espacoId, entidadeId)
                        : contaRepository.findByEspacoId(espacoId)).stream()
                        .map(Conta::getSaldo).reduce(BigDecimal.ZERO, BigDecimal::add);

        var fonte = entidadeId != null
                ? transacaoRepository.findByEspacoIdAndDataVencimentoBetweenFiltradoPorEntidade(espacoId, hoje, fim, entidadeId)
                : transacaoRepository.findByEspacoIdAndDataVencimentoBetweenOrderByDataVencimentoAsc(espacoId, hoje, fim);

        List<Transacao> pendencias = fonte.stream()
                .filter(t -> t.getDataPagamento() == null && t.getDataCancelamento() == null)
                .filter(t -> contaId == null || t.getConta().getId().equals(contaId))
                .toList();

        Map<LocalDate, BigDecimal> deltaPorDia = pendencias.stream()
                .collect(Collectors.groupingBy(Transacao::getDataVencimento,
                        Collectors.reducing(BigDecimal.ZERO, this::computeDelta, BigDecimal::add)));

        boolean temSimulacao = simulacaoValor != null && simulacaoData != null;

        List<ProjecaoDTO.Ponto> pontos = new ArrayList<>();
        BigDecimal acumulado = saldoAtual;
        BigDecimal acumuladoSimulado = saldoAtual;
        for (int i = 0; i <= dias; i++) {
            LocalDate dia = hoje.plusDays(i);
            acumulado = acumulado.add(deltaPorDia.getOrDefault(dia, BigDecimal.ZERO));
            if (temSimulacao) {
                acumuladoSimulado = acumuladoSimulado.add(deltaPorDia.getOrDefault(dia, BigDecimal.ZERO));
                if (dia.isEqual(simulacaoData)) {
                    acumuladoSimulado = acumuladoSimulado.subtract(simulacaoValor);
                }
            }
            pontos.add(ProjecaoDTO.Ponto.builder()
                    .data(dia)
                    .saldo(acumulado)
                    .saldoSimulado(temSimulacao ? acumuladoSimulado : null)
                    .build());
        }

        return ProjecaoDTO.builder().saldoAtual(saldoAtual).pontos(pontos).build();
    }

    // Espelha TransacaoService.computeDelta (transferência depende da direção,
    // não do tipo) — mantido local porque é a única outra classe que precisa
    // do sinal de uma transação sem tocar saldo de verdade.
    private BigDecimal computeDelta(Transacao t) {
        if (t.getTipo() == TipoTransacao.TRANSFERENCIA) {
            return t.getDirecaoTransferencia() == DirecaoTransferencia.ENTRADA ? t.getValor() : t.getValor().negate();
        }
        return t.getTipo() == TipoTransacao.RECEITA ? t.getValor() : t.getValor().negate();
    }
}
