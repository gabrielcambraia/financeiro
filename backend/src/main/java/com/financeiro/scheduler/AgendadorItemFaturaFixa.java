package com.financeiro.scheduler;

import com.financeiro.entity.Fatura;
import com.financeiro.entity.ItemFatura;
import com.financeiro.entity.Transacao;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.service.ConversaoLancamentoService;
import com.financeiro.service.ContaService;
import com.financeiro.service.ResolvedorFaturaAlvo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorItemFaturaFixa {

    private final ItemFaturaRepository repository;
    private final TransacaoRepository transacaoRepository;
    private final ContaService contaService;
    private final ResolvedorFaturaAlvo resolvedorFaturaAlvo;
    private final ConversaoLancamentoService conversaoLancamentoService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        log.info("Verificando itens de fatura fixos na inicialização...");
        process();
    }

    @Scheduled(cron = "0 5 0 1 * *")
    @Transactional
    public void onFirstOfMonth() {
        log.info("Processando itens de fatura fixos (dia 1° do mês)...");
        process();
    }

    private void process() {
        try {
            MDC.put("idRequisicao", "agendador-item-fatura-fixa-" + UUID.randomUUID());

            YearMonth mesAtual = YearMonth.from(LocalDate.now());

            List<Long> espacos = repository.findEspacosComItemFixaAtivaNoPeriodo(
                    mesAtual.atDay(1), mesAtual.atEndOfMonth());
            for (Long espacoId : espacos) {
                for (int offset = 1; offset <= 12; offset++) {
                    extendTo(mesAtual, mesAtual.plusMonths(offset), espacoId);
                }
            }
        } finally {
            MDC.clear();
        }
    }

    private void extendTo(YearMonth mesOrigem, YearMonth mesAlvo, Long espacoId) {
        List<ItemFatura> modelos = repository.findByEspacoIdAndFixaTrueAndSerieAtivaTrueAndDataBetween(
                espacoId, mesOrigem.atDay(1), mesOrigem.atEndOfMonth());

        for (ItemFatura original : modelos) {
            if (original.getOrigemFixaId() == null) {
                original.setOrigemFixaId(original.getId());
                original.setSerieAtiva(true);
                repository.save(original);
            }

            boolean existe = repository.existsByEspacoIdAndOrigemFixaIdAndDataBetween(
                    original.getEspacoId(), original.getOrigemFixaId(),
                    mesAlvo.atDay(1), mesAlvo.atEndOfMonth());

            if (!existe) {
                int dia = Math.min(original.getData().getDayOfMonth(), mesAlvo.lengthOfMonth());
                LocalDate dataCopia = mesAlvo.atDay(dia);

                Fatura faturaAlvo = resolvedorFaturaAlvo.resolver(original.getCartao(), dataCopia);

                if (faturaAlvo != null) {
                    Transacao despesa = faturaAlvo.getTransacaoDespesa();
                    if (despesa.getDataPagamento() != null || despesa.getDataCancelamento() != null) {
                        log.warn("Pulando {} para item fixa {} — fatura paga/cancelada", mesAlvo, original.getId());
                        continue;
                    }
                }

                ItemFatura copia = ItemFatura.builder()
                        .cartao(original.getCartao())
                        .categoria(original.getCategoria())
                        .valor(original.getValor())
                        .descricao(original.getDescricao())
                        .data(dataCopia)
                        .fatura(faturaAlvo)
                        .fixa(true)
                        .serieAtiva(true)
                        .origemFixaId(original.getOrigemFixaId())
                        .espacoId(original.getEspacoId())
                        .usuarioId(original.getUsuarioId())
                        .build();

                repository.save(copia);

                if (faturaAlvo != null) {
                    conversaoLancamentoService.reconciliarFaturaFechadaAdicao(faturaAlvo, copia.getValor());
                }

                log.info("Item fixa criado para {} (cartão={})", mesAlvo, original.getCartao().getId());
            }
        }
    }
}
