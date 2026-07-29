package com.financeiro.scheduler;

import com.financeiro.entity.Ativo;
import com.financeiro.entity.IndiceEconomico;
import com.financeiro.entity.enums.TipoRemuneracao;
import com.financeiro.repository.AtivoRepository;
import com.financeiro.repository.IndiceEconomicoRepository;
import com.financeiro.service.AtivoService;
import com.financeiro.service.ServicoIndiceEconomico;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Credita rendimento automático (CDI/Selic/IPCA+/pré-fixado) dos ativos que
 * têm um indexador configurado ({@code remuneracaoTipo != NENHUMA}). Segue o
 * mesmo padrão de {@link AgendadorTransacaoFixa}: roda no
 * {@code ApplicationReadyEvent} do startup — o app pode ficar dias sem
 * tráfego, então o startup garante que meses perdidos sejam processados ao
 * subir novamente — e também no dia 1° do mês, 15 minutos depois do
 * agendador de transação fixa.
 *
 * <p>Roda sem contexto de request/usuário: processa todos os espaços de uma
 * vez, já que {@code Ativo} carrega seu próprio {@code espacoId}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorRendimento {

    private final AtivoRepository ativoRepository;
    private final AtivoService ativoService;
    private final IndiceEconomicoRepository indiceRepository;
    private final ServicoIndiceEconomico servicoIndiceEconomico;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        log.info("Verificando rendimento automático de investimentos na inicialização...");
        process();
    }

    @Scheduled(cron = "0 15 0 1 * *")
    @Transactional
    public void onFirstOfMonth() {
        log.info("Processando rendimento automático de investimentos (dia 1° do mês)...");
        process();
    }

    private void process() {
        try {
            MDC.put("idRequisicao", "agendador-" + UUID.randomUUID());

            // Sem chamada de rede não derruba nada (ver ServicoIndiceEconomico) — só
            // deixa os meses sem índice ainda pendentes pra próxima execução.
            servicoIndiceEconomico.atualizarTodos();

            YearMonth ultimoMesFechado = YearMonth.now().minusMonths(1);
            List<Ativo> ativos = ativoRepository.findByDataCancelamentoIsNullAndRemuneracaoTipoNot(TipoRemuneracao.NENHUMA);
            for (Ativo ativo : ativos) {
                processarAtivo(ativo, ultimoMesFechado);
            }
        } finally {
            MDC.clear();
        }
    }

    private void processarAtivo(Ativo ativo, YearMonth ultimoMesFechado) {
        YearMonth mesAtual = primeiroMesAProcessar(ativo);
        if (mesAtual == null) {
            return; // ainda sem início de rendimento definido (nenhum aporte feito ainda)
        }

        while (!mesAtual.isAfter(ultimoMesFechado)) {
            BigDecimal indiceMes = obterIndiceMes(ativo, mesAtual);
            boolean precisaIndice = ativo.getRemuneracaoTipo() != TipoRemuneracao.PRE_FIXADA;
            if (precisaIndice && indiceMes == null) {
                // Índice daquele mês ainda não chegou do Banco Central — para o loop
                // deste ativo SEM avançar rendidoAte; retoma exatamente dali na
                // próxima execução (idempotência).
                log.info("Índice do mês {} indisponível para o ativo {} ({}) — retomando na próxima execução",
                        mesAtual, ativo.getId(), ativo.getRemuneracaoTipo());
                break;
            }

            BigDecimal taxaMes = calcularTaxaMes(ativo, indiceMes);
            LocalDate fimMes = mesAtual.atEndOfMonth();
            BigDecimal base = ativoService.saldoEm(ativo.getId(), fimMes);
            BigDecimal valor = base.multiply(taxaMes).setScale(2, RoundingMode.HALF_UP);

            if (valor.compareTo(BigDecimal.ZERO) > 0) {
                ativoService.creditarRendimentoAutomatico(ativo, valor, fimMes);
            }
            ativo.setRendidoAte(fimMes);
            ativoRepository.save(ativo);

            mesAtual = mesAtual.plusMonths(1);
        }
    }

    private YearMonth primeiroMesAProcessar(Ativo ativo) {
        if (ativo.getRendidoAte() != null) {
            return YearMonth.from(ativo.getRendidoAte()).plusMonths(1);
        }
        if (ativo.getInicioRendimento() != null) {
            return YearMonth.from(ativo.getInicioRendimento());
        }
        return null;
    }

    private BigDecimal obterIndiceMes(Ativo ativo, YearMonth mes) {
        String codigo = switch (ativo.getRemuneracaoTipo()) {
            case POS_CDI -> ServicoIndiceEconomico.CDI;
            case POS_SELIC -> ServicoIndiceEconomico.SELIC;
            case IPCA_MAIS -> ServicoIndiceEconomico.IPCA;
            case PRE_FIXADA, NENHUMA -> null;
        };
        if (codigo == null) {
            return null;
        }
        return indiceRepository.findByCodigoAndMes(codigo, mes.toString())
                .map(IndiceEconomico::getValor)
                .orElse(null);
    }

    /**
     * Taxa (fração, não percentual) a aplicar sobre a base do mês. {@code indiceMesPercentual}
     * é o valor bruto salvo em {@code indices_economicos} (percentual do mês, ex.: 0.85 = 0,85%).
     */
    private BigDecimal calcularTaxaMes(Ativo ativo, BigDecimal indiceMesPercentual) {
        BigDecimal taxa = ativo.getTaxa() != null ? ativo.getTaxa() : BigDecimal.ZERO;
        return switch (ativo.getRemuneracaoTipo()) {
            case PRE_FIXADA -> taxaMensalEquivalente(taxa);
            case POS_CDI, POS_SELIC -> fracao(indiceMesPercentual).multiply(taxa.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
            case IPCA_MAIS -> {
                BigDecimal ipcaFracao = fracao(indiceMesPercentual);
                BigDecimal spreadMensal = taxaMensalEquivalente(taxa);
                yield BigDecimal.ONE.add(ipcaFracao).multiply(BigDecimal.ONE.add(spreadMensal)).subtract(BigDecimal.ONE);
            }
            case NENHUMA -> BigDecimal.ZERO;
        };
    }

    private BigDecimal fracao(BigDecimal percentual) {
        return percentual.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
    }

    /** (1 + taxaAnual%/100)^(1/12) - 1 — equivalente mensal de uma taxa anual (pré-fixada ou spread do IPCA+). */
    private BigDecimal taxaMensalEquivalente(BigDecimal taxaAnualPercentual) {
        double anual = taxaAnualPercentual.doubleValue() / 100.0;
        double mensal = Math.pow(1 + anual, 1.0 / 12) - 1;
        return BigDecimal.valueOf(mensal);
    }
}
