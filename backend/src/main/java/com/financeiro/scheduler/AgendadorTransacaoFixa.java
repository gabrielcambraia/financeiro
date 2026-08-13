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
            //
            // Processa um espaço por vez para não carregar todas as fixas de todos
            // os tenants em memória de uma só vez.
            List<Long> espacos = repository.findEspacosComFixaAtivaNoPeriodo(
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
        List<Transacao> modelos = repository.findByEspacoIdAndFixaTrueAndSerieAtivaTrueAndDataBetween(
                espacoId, mesOrigem.atDay(1), mesOrigem.atEndOfMonth());

        for (Transacao original : modelos) {
            if (original.getOrigemFixaId() == null) {
                // Linha legada (antes da V23): auto-sana como cabeça da própria série.
                original.setOrigemFixaId(original.getId());
                original.setSerieAtiva(true);
                repository.save(original);
            }
            boolean existe = repository.existsByEspacoIdAndOrigemFixaIdAndDataBetween(
                    original.getEspacoId(),
                    original.getOrigemFixaId(),
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
                        .debitoAutomatico(original.isDebitoAutomatico())
                        .saldoAjustado(false)
                        .espacoId(original.getEspacoId())
                        .usuarioId(original.getUsuarioId())
                        .origemFixaId(original.getOrigemFixaId())
                        .serieAtiva(true)
                        .entidadeId(original.getEntidadeId())
                        .build();
                repository.save(copia);
                log.info("Entrada criada para {} (conta={})", mesAlvo, original.getConta().getId());
            }
        }
    }
}
