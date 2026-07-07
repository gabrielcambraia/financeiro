package com.financeiro.scheduler;

import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.service.ContaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorTransacaoFixa {

    private final TransacaoRepository repository;
    private final ContaService contaService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        log.info("Verificando transações fixas na inicialização...");
        process();
    }

    @Scheduled(cron = "0 5 0 1 * *")
    @Transactional
    public void onFirstOfMonth() {
        log.info("Processando transações fixas (dia 1° do mês)...");
        process();
    }

    private void process() {
        YearMonth mesAtual = YearMonth.from(LocalDate.now());

        // 1. Ajusta saldo de qualquer transação (fixa ou não) cuja data já chegou
        List<Transacao> vencidas = repository.findBySaldoAjustadoFalseAndDataLessThanEqual(
                LocalDate.now().toString());

        for (Transacao t : vencidas) {
            contaService.adjustBalance(t.getConta(),
                    t.getTipo() == TipoTransacao.RECEITA ? t.getValor() : t.getValor().negate());
            t.setSaldoAjustado(true);
            repository.save(t);
            log.info("Saldo ajustado: id={} data={}", t.getId(), t.getData());
        }

        // 2. Garante janela de 12 meses à frente para cada transação fixa ativa
        for (int offset = 1; offset <= 12; offset++) {
            YearMonth mesAlvo = mesAtual.plusMonths(offset);
            extendTo(mesAtual, mesAlvo);
        }
    }

    private void extendTo(YearMonth mesOrigem, YearMonth mesAlvo) {
        List<Transacao> modelos = repository.findByFixaTrueAndDataBetween(
                mesOrigem.atDay(1).toString(), mesOrigem.atEndOfMonth().toString());

        for (Transacao original : modelos) {
            boolean existe = repository.existsByFixaTrueAndContaIdAndValorAndTipoAndDescricaoAndDataBetween(
                    original.getConta().getId(),
                    original.getValor(),
                    original.getTipo(),
                    original.getDescricao(),
                    mesAlvo.atDay(1).toString(),
                    mesAlvo.atEndOfMonth().toString());

            if (!existe) {
                int dia = Math.min(LocalDate.parse(original.getData()).getDayOfMonth(), mesAlvo.lengthOfMonth());
                Transacao copia = Transacao.builder()
                        .conta(original.getConta())
                        .categoria(original.getCategoria())
                        .tipo(original.getTipo())
                        .tipoPagamento(original.getTipoPagamento())
                        .valor(original.getValor())
                        .descricao(original.getDescricao())
                        .data(mesAlvo.atDay(dia).toString())
                        .fixa(true)
                        .saldoAjustado(false)
                        .build();
                repository.save(copia);
                log.info("Entrada criada para {} (conta={})", mesAlvo, original.getConta().getId());
            }
        }
    }
}
