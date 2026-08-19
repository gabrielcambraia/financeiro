package com.financeiro.scheduler;

import com.financeiro.entity.Recorrencia;
import com.financeiro.repository.RecorrenciaRepository;
import com.financeiro.service.GeradorLancamentoRecorrencia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorRecorrencia {

    private final RecorrenciaRepository repository;
    private final GeradorLancamentoRecorrencia gerador;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Verificando recorrências na inicialização...");
        processarMes(YearMonth.now());
    }

    // Dia 1 de cada mês às 01:00
    @Scheduled(cron = "0 0 1 1 * *")
    public void onDiaUm() {
        log.info("Processando recorrências (dia 1° do mês)...");
        processarMes(YearMonth.now());
    }

    private void processarMes(YearMonth mes) {
        try {
            MDC.put("idRequisicao", "agendador-recorrencia-" + UUID.randomUUID());
            List<Recorrencia> ativas = repository.findAtivasParaMes(mes.atDay(1), mes.atEndOfMonth());
            log.info("Processando {} recorrência(s) ativa(s) para {}", ativas.size(), mes);
            for (Recorrencia r : ativas) {
                try {
                    gerador.gerarParaMes(r, mes);
                } catch (Exception ex) {
                    log.error("Erro ao gerar lançamento da recorrência {} em {}: {}", r.getId(), mes, ex.getMessage(), ex);
                }
            }
        } finally {
            MDC.clear();
        }
    }
}
