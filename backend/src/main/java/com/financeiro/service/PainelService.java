package com.financeiro.service;

import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.PainelDTO;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PainelService {

    private final TransacaoRepository transacaoRepository;
    private final ContaRepository contaRepository;
    private final ContaService contaService;

    public PainelDTO getDashboard(String month, Long contaId) {
        YearMonth ym = YearMonth.parse(month);
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();
        String today = LocalDate.now().toString();

        List<Transacao> mesTx = fetch(contaId, start, end, false);

        List<Transacao> realizadasTx = mesTx.stream()
                .filter(t -> t.getData().compareTo(today) <= 0).toList();
        List<Transacao> pendentesTx = mesTx.stream()
                .filter(t -> t.getData().compareTo(today) > 0).toList();

        BigDecimal totalReceitas = sum(mesTx, TipoTransacao.RECEITA);
        BigDecimal totalDespesas = sum(mesTx, TipoTransacao.DESPESA);

        return PainelDTO.builder()
                .totalReceitas(totalReceitas)
                .totalDespesas(totalDespesas)
                .saldoLiquido(totalReceitas.subtract(totalDespesas))
                .realizado(buildResumoFluxo(realizadasTx))
                .pendente(buildResumoFluxo(pendentesTx))
                .despesasPorCategoria(buildResumoCategoria(mesTx, TipoTransacao.DESPESA, totalDespesas))
                .receitasPorCategoria(buildResumoCategoria(mesTx, TipoTransacao.RECEITA, totalReceitas))
                .tendenciaMensal(buildTendenciaMensal(ym, contaId))
                .saldosContas(buildSaldosContas())
                .saldoDiario(buildSaldoDiario(mesTx, ym))
                .build();
    }

    private PainelDTO.ResumoFluxo buildResumoFluxo(List<Transacao> transacoes) {
        BigDecimal receita = sum(transacoes, TipoTransacao.RECEITA);
        BigDecimal despesa = sum(transacoes, TipoTransacao.DESPESA);
        return PainelDTO.ResumoFluxo.builder()
                .receita(receita)
                .despesa(despesa)
                .saldo(receita.subtract(despesa))
                .build();
    }

    private List<Transacao> fetch(Long contaId, String start, String end, boolean asc) {
        if (contaId != null) {
            return asc
                    ? transacaoRepository.findByContaIdAndDataBetweenOrderByDataAsc(contaId, start, end)
                    : transacaoRepository.findByContaIdAndDataBetweenOrderByDataDesc(contaId, start, end);
        }
        return asc
                ? transacaoRepository.findByDataBetweenOrderByDataAsc(start, end)
                : transacaoRepository.findByDataBetweenOrderByDataDesc(start, end);
    }

    private BigDecimal sum(List<Transacao> transacoes, TipoTransacao tipo) {
        return transacoes.stream()
                .filter(t -> t.getTipo() == tipo)
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PainelDTO.ResumoCategoria> buildResumoCategoria(
            List<Transacao> transacoes, TipoTransacao tipo, BigDecimal total) {
        Map<Long, List<Transacao>> agrupado = transacoes.stream()
                .filter(t -> t.getTipo() == tipo && t.getCategoria() != null)
                .collect(Collectors.groupingBy(t -> t.getCategoria().getId()));

        return agrupado.entrySet().stream().map(entry -> {
            Transacao amostra = entry.getValue().get(0);
            BigDecimal totalCategoria = entry.getValue().stream()
                    .map(Transacao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            double pct = total.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : totalCategoria.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100;

            CategoriaDTO catDTO = new CategoriaDTO();
            catDTO.setId(amostra.getCategoria().getId());
            catDTO.setNome(amostra.getCategoria().getNome());
            catDTO.setTipo(amostra.getCategoria().getTipo());
            catDTO.setCor(amostra.getCategoria().getCor());
            catDTO.setIcone(amostra.getCategoria().getIcone());

            return PainelDTO.ResumoCategoria.builder()
                    .categoria(catDTO).total(totalCategoria).percentual(pct).build();
        }).sorted(Comparator.comparing(PainelDTO.ResumoCategoria::getTotal).reversed()).toList();
    }

    private List<PainelDTO.TendenciaMensal> buildTendenciaMensal(YearMonth atual, Long contaId) {
        String dataInicio = atual.minusMonths(5).atDay(1).toString();
        String dataFim = atual.atEndOfMonth().toString();

        List<Transacao> todas = fetch(contaId, dataInicio, dataFim, true);

        List<PainelDTO.TendenciaMensal> tendencia = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = atual.minusMonths(i);
            String ms = m.atDay(1).toString();
            String me = m.atEndOfMonth().toString();
            List<Transacao> mesTx = todas.stream()
                    .filter(t -> t.getData().compareTo(ms) >= 0 && t.getData().compareTo(me) <= 0)
                    .toList();
            tendencia.add(PainelDTO.TendenciaMensal.builder()
                    .mes(m.toString())
                    .receita(sum(mesTx, TipoTransacao.RECEITA))
                    .despesa(sum(mesTx, TipoTransacao.DESPESA))
                    .build());
        }
        return tendencia;
    }

    private List<PainelDTO.SaldoConta> buildSaldosContas() {
        return contaRepository.findAll().stream().map(c ->
                PainelDTO.SaldoConta.builder()
                        .conta(contaService.toDTO(c))
                        .saldo(c.getSaldo())
                        .build()
        ).toList();
    }

    private List<PainelDTO.SaldoDiario> buildSaldoDiario(List<Transacao> transacoes, YearMonth ym) {
        List<PainelDTO.SaldoDiario> resultado = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int dia = 1; dia <= ym.lengthOfMonth(); dia++) {
            String dataStr = ym.atDay(dia).toString();
            for (Transacao t : transacoes) {
                if (dataStr.equals(t.getData())) {
                    acumulado = acumulado.add(t.getTipo() == TipoTransacao.RECEITA
                            ? t.getValor() : t.getValor().negate());
                }
            }
            resultado.add(PainelDTO.SaldoDiario.builder().data(dataStr).saldo(acumulado).build());
        }
        return resultado;
    }
}
