package com.financeiro.scheduler;

import com.financeiro.entity.Transacao;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.service.ContaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

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
        try {
            MDC.put("idRequisicao", "agendador-" + UUID.randomUUID());

            YearMonth mesAtual = YearMonth.from(LocalDate.now());

            // Quitação de transações é manual (ver TransacaoService.pagar): o
            // agendador NÃO ajusta mais saldo sozinho quando uma data é alcançada.
            // Ele só garante que as transações fixas continuem sendo pré-criadas
            // com 12 meses de antecedência, sempre nascendo PENDENTES.
            for (int offset = 1; offset <= 12; offset++) {
                YearMonth mesAlvo = mesAtual.plusMonths(offset);
                extendTo(mesAtual, mesAlvo);
            }
        } finally {
            MDC.clear();
        }
    }

    // Nota: este agendador roda em background (startup + cron mensal), sem
    // contexto de request/usuário. Por isso processa TODOS os espaços de uma
    // vez em vez de usar ContextoEspaco — cada transação já carrega seu
    // próprio espacoId (herdado da conta original), então maturar/estender
    // por linha nunca mistura dados entre espaços.
    private void extendTo(YearMonth mesOrigem, YearMonth mesAlvo) {
        List<Transacao> modelos = repository.findByFixaTrueAndDataBetween(
                mesOrigem.atDay(1), mesOrigem.atEndOfMonth());

        for (Transacao original : modelos) {
            boolean existe = repository.existsByEspacoIdAndFixaTrueAndContaIdAndValorAndTipoAndDescricaoAndDataBetween(
                    original.getEspacoId(),
                    original.getConta().getId(),
                    original.getValor(),
                    original.getTipo(),
                    original.getDescricao(),
                    mesAlvo.atDay(1), mesAlvo.atEndOfMonth());

            if (!existe) {
                int dia = Math.min(original.getData().getDayOfMonth(), mesAlvo.lengthOfMonth());
                LocalDate dataCopia = mesAlvo.atDay(dia);
                Transacao copia = Transacao.builder()
                        .conta(original.getConta())
                        .categoria(original.getCategoria())
                        .tipo(original.getTipo())
                        .tipoPagamento(original.getTipoPagamento())
                        .valor(original.getValor())
                        .descricao(original.getDescricao())
                        .data(dataCopia)
                        .dataVencimento(dataCopia)
                        .fixa(true)
                        .saldoAjustado(false)
                        .espacoId(original.getEspacoId())
                        .usuarioId(original.getUsuarioId())
                        .build();
                repository.save(copia);
                log.info("Entrada criada para {} (conta={})", mesAlvo, original.getConta().getId());
            }
        }
    }
}
