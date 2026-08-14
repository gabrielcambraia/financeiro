package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.CentroCustoDTO;
import com.financeiro.dto.PainelDTO;
import com.financeiro.entity.CentroCusto;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.DirecaoTransferencia;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PainelService {

    private static final int DIAS_JANELA_VENCIMENTO = 7;
    private static final int MAX_ITENS_VENCIMENTO = 5;

    private final TransacaoRepository transacaoRepository;
    private final ContaRepository contaRepository;
    private final ContaService contaService;
    private final ContextoEspaco contextoEspaco;
    private final ContextoEntidade contextoEntidade;

    public PainelDTO getDashboard(String month, Long contaId) {
        Long espacoId = contextoEspaco.espacoAtual();
        YearMonth ym = YearMonth.parse(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Transacao> mesTx = fetch(espacoId, contaId, start, end, false);

        // Canceladas nunca aconteceram de fato — ficam fora de qualquer total.
        List<Transacao> ativasTx = mesTx.stream()
                .filter(t -> t.getDataCancelamento() == null).toList();

        // Quitação é manual (ver TransacaoService): "realizado" é quem já foi
        // pago (saldoAjustado), não mais quem tem data <= hoje.
        List<Transacao> realizadasTx = ativasTx.stream()
                .filter(Transacao::isSaldoAjustado).toList();
        List<Transacao> pendentesTx = ativasTx.stream()
                .filter(t -> !t.isSaldoAjustado()).toList();

        BigDecimal totalReceitas = sum(ativasTx, TipoTransacao.RECEITA);
        BigDecimal totalDespesas = sum(ativasTx, TipoTransacao.DESPESA);

        return PainelDTO.builder()
                .totalReceitas(totalReceitas)
                .totalDespesas(totalDespesas)
                .saldoLiquido(totalReceitas.subtract(totalDespesas))
                .realizado(buildResumoFluxo(realizadasTx))
                .pendente(buildResumoFluxo(pendentesTx))
                .despesasPorCategoria(buildResumoCategoria(ativasTx, TipoTransacao.DESPESA, totalDespesas))
                .receitasPorCategoria(buildResumoCategoria(ativasTx, TipoTransacao.RECEITA, totalReceitas))
                .despesasPorCentroCusto(buildResumoCentroCusto(ativasTx, totalDespesas))
                .tendenciaMensal(buildTendenciaMensal(espacoId, ym, contaId))
                .saldosContas(buildSaldosContas(espacoId, contaId))
                .saldoDiario(buildSaldoDiario(ativasTx, ym))
                .vencimentos(buildVencimentos(espacoId, contaId))
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

    private List<Transacao> fetch(Long espacoId, Long contaId, LocalDate start, LocalDate end, boolean asc) {
        Long entidadeId = contextoEntidade.entidadeAtual();
        if (entidadeId != null) {
            if (contaId != null) {
                return asc
                        ? transacaoRepository.findByEspacoIdAndContaIdAndDataBetweenAscFiltradoPorEntidade(espacoId, contaId, start, end, entidadeId)
                        : transacaoRepository.findByEspacoIdAndContaIdAndDataBetweenFiltradoPorEntidade(espacoId, contaId, start, end, entidadeId);
            }
            return asc
                    ? transacaoRepository.findByEspacoIdAndDataBetweenAscFiltradoPorEntidade(espacoId, start, end, entidadeId)
                    : transacaoRepository.findByEspacoIdAndDataBetweenFiltradoPorEntidade(espacoId, start, end, entidadeId);
        }
        if (contaId != null) {
            return asc
                    ? transacaoRepository.findByEspacoIdAndContaIdAndDataBetweenOrderByDataAsc(espacoId, contaId, start, end)
                    : transacaoRepository.findByEspacoIdAndContaIdAndDataBetweenOrderByDataDesc(espacoId, contaId, start, end);
        }
        return asc
                ? transacaoRepository.findByEspacoIdAndDataBetweenOrderByDataAsc(espacoId, start, end)
                : transacaoRepository.findByEspacoIdAndDataBetweenOrderByDataDesc(espacoId, start, end);
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

    private List<PainelDTO.ResumoCentroCusto> buildResumoCentroCusto(
            List<Transacao> transacoes, BigDecimal totalDespesas) {

        Map<Long, List<Transacao>> agrupado = transacoes.stream()
                .filter(t -> t.getTipo() == TipoTransacao.DESPESA && t.getCentroCusto() != null)
                .collect(Collectors.groupingBy(Transacao::getCentroCustoId));

        return agrupado.entrySet().stream().map(entry -> {
            CentroCusto cc = entry.getValue().get(0).getCentroCusto();
            BigDecimal totalCC = entry.getValue().stream()
                    .map(Transacao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            double pct = totalDespesas.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : totalCC.divide(totalDespesas, 4, RoundingMode.HALF_UP).doubleValue() * 100;

            CentroCustoDTO ccDTO = new CentroCustoDTO();
            ccDTO.setId(cc.getId());
            ccDTO.setNome(cc.getNome());
            ccDTO.setCor(cc.getCor());
            ccDTO.setEntidadeId(cc.getEntidadeId());

            return PainelDTO.ResumoCentroCusto.builder()
                    .centroCusto(ccDTO).total(totalCC).percentual(pct).build();
        }).sorted(Comparator.comparing(PainelDTO.ResumoCentroCusto::getTotal).reversed()).toList();
    }

    private List<PainelDTO.TendenciaMensal> buildTendenciaMensal(Long espacoId, YearMonth atual, Long contaId) {
        LocalDate dataInicio = atual.minusMonths(11).atDay(1);
        LocalDate dataFim = atual.atEndOfMonth();

        List<Transacao> todas = fetch(espacoId, contaId, dataInicio, dataFim, true).stream()
                .filter(t -> t.getDataCancelamento() == null).toList();

        List<PainelDTO.TendenciaMensal> tendencia = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth m = atual.minusMonths(i);
            LocalDate ms = m.atDay(1);
            LocalDate me = m.atEndOfMonth();
            List<Transacao> mesTx = todas.stream()
                    .filter(t -> !t.getData().isBefore(ms) && !t.getData().isAfter(me))
                    .toList();
            tendencia.add(PainelDTO.TendenciaMensal.builder()
                    .mes(m.toString())
                    .receita(sum(mesTx, TipoTransacao.RECEITA))
                    .despesa(sum(mesTx, TipoTransacao.DESPESA))
                    .build());
        }
        return tendencia;
    }

    private List<PainelDTO.SaldoConta> buildSaldosContas(Long espacoId, Long contaId) {
        Long entidadeId = contextoEntidade.entidadeAtual();
        var contas = entidadeId != null
                ? contaRepository.findByEspacoIdFiltradoPorEntidade(espacoId, entidadeId)
                : contaRepository.findByEspacoId(espacoId);
        if (contaId != null) {
            contas = contas.stream().filter(c -> c.getId().equals(contaId)).toList();
        }
        return contas.stream().map(c ->
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
            LocalDate data = ym.atDay(dia);
            for (Transacao t : transacoes) {
                if (data.equals(t.getData())) {
                    acumulado = acumulado.add(computeDelta(t));
                }
            }
            resultado.add(PainelDTO.SaldoDiario.builder().data(data).saldo(acumulado).build());
        }
        return resultado;
    }

    // Espelha TransacaoService.computeDelta: transferência não é RECEITA nem
    // DESPESA — o sinal depende da direção (SAIDA/ENTRADA), não do tipo.
    private BigDecimal computeDelta(Transacao t) {
        if (t.getTipo() == TipoTransacao.TRANSFERENCIA) {
            return t.getDirecaoTransferencia() == DirecaoTransferencia.ENTRADA ? t.getValor() : t.getValor().negate();
        }
        return t.getTipo() == TipoTransacao.RECEITA ? t.getValor() : t.getValor().negate();
    }

    // Vencidas (sem limite inferior, mesmo fora do mês filtrado) + a vencer nos
    // próximos DIAS_JANELA_VENCIMENTO dias. É um alerta, não uma visão de
    // competência: independe do mês selecionado no painel, mas respeita a
    // conta selecionada (contaId), assim como "Contas".
    private PainelDTO.Vencimentos buildVencimentos(Long espacoId, Long contaId) {
        LocalDate hoje = LocalDate.now();
        LocalDate limiteAVencer = hoje.plusDays(DIAS_JANELA_VENCIMENTO);

        return PainelDTO.Vencimentos.builder()
                .aPagar(buildGrupoVencimento(espacoId, contaId, TipoTransacao.DESPESA, hoje, limiteAVencer))
                .aReceber(buildGrupoVencimento(espacoId, contaId, TipoTransacao.RECEITA, hoje, limiteAVencer))
                .build();
    }

    // "Vencidas" é buscada com contagem/soma agregadas (não trazem linha
    // nenhuma) + só os MAX_ITENS_VENCIMENTO mais antigos via Pageable — sem
    // isso, a lista de vencidas cresceria sem limite conforme o histórico do
    // espaço, mesmo que só os primeiros itens sejam exibidos. "A vencer" já é
    // naturalmente limitada pela janela de DIAS_JANELA_VENCIMENTO dias.
    private PainelDTO.GrupoVencimento buildGrupoVencimento(Long espacoId, Long contaId, TipoTransacao tipo, LocalDate hoje, LocalDate limiteAVencer) {
        long quantidadeVencida = transacaoRepository.countVencidas(espacoId, contaId, tipo, hoje);
        BigDecimal totalVencido = transacaoRepository.somaVencidas(espacoId, contaId, tipo, hoje);
        List<Transacao> vencidas = transacaoRepository
                .findVencidas(espacoId, contaId, tipo, hoje, PageRequest.of(0, MAX_ITENS_VENCIMENTO));

        List<Transacao> aVencer = transacaoRepository.findAVencer(espacoId, contaId, tipo, hoje, limiteAVencer);

        return PainelDTO.GrupoVencimento.builder()
                .totalVencido(totalVencido)
                .quantidadeVencida((int) quantidadeVencida)
                .vencidas(vencidas.stream().map(t -> toItemVencimento(t, hoje)).toList())
                .totalAVencer(sum(aVencer, tipo))
                .quantidadeAVencer(aVencer.size())
                .aVencer(aVencer.stream().limit(MAX_ITENS_VENCIMENTO).map(t -> toItemVencimento(t, hoje)).toList())
                .build();
    }

    private PainelDTO.ItemVencimento toItemVencimento(Transacao t, LocalDate hoje) {
        String descricao = t.getDescricao() != null && !t.getDescricao().isBlank()
                ? t.getDescricao()
                : (t.getCategoria() != null ? t.getCategoria().getNome() : "Sem descrição");

        return PainelDTO.ItemVencimento.builder()
                .id(t.getId())
                .descricao(descricao)
                .valor(t.getValor())
                .dataVencimento(t.getDataVencimento())
                .diasEmRelacaoAHoje(ChronoUnit.DAYS.between(hoje, t.getDataVencimento()))
                .contaNome(t.getConta().getNome())
                .build();
    }
}
