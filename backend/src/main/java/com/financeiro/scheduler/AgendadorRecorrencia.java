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

import java.time.LocalDate;
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
        processarAtePresente();
    }

    // Dia 1 de cada mês às 01:00
    @Scheduled(cron = "0 0 1 1 * *")
    public void onDiaUm() {
        log.info("Processando recorrências (dia 1° do mês)...");
        processarAtePresente();
    }

    // Processa do mês mais antigo com recorrência ativa até o mês atual, para
    // recuperar meses inteiros perdidos quando o app fica dias/semanas sem
    // subir (idempotente: gerarParaMes() não duplica lançamento já gerado).
    private void processarAtePresente() {
        try {
            MDC.put("idRequisicao", "agendador-recorrencia-" + UUID.randomUUID());
            YearMonth atual = YearMonth.now();
            YearMonth maisAntigo = repository.findDataInicioMaisAntigaAtivaVigente(LocalDate.now())
                    .map(YearMonth::from)
                    .orElse(atual);
            if (maisAntigo.isAfter(atual)) {
                maisAntigo = atual;
            }
            for (YearMonth mes = maisAntigo; !mes.isAfter(atual); mes = mes.plusMonths(1)) {
                processarMes(mes);
            }
        } finally {
            MDC.clear();
        }
    }

    private void processarMes(YearMonth mes) {
        List<Recorrencia> ativas = repository.findAtivasParaMes(mes.atDay(1), mes.atEndOfMonth());
        log.info("Processando {} recorrência(s) ativa(s) para {}", ativas.size(), mes);
        for (Recorrencia r : ativas) {
            try {
                gerador.gerarParaMes(r, mes);
            } catch (Exception ex) {
                log.error("Erro ao gerar lançamento da recorrência {} em {}: {}", r.getId(), mes, ex.getMessage(), ex);
            }
        }
    }
}
