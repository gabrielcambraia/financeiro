package com.financeiro.scheduler;

import com.financeiro.entity.Transacao;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.service.TransacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorDebitoAutomatico {

    private final TransacaoRepository repository;
    private final TransacaoService transacaoService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Verificando débitos automáticos na inicialização...");
        process();
    }

    @Scheduled(cron = "0 10 0 * * *")
    public void diario() {
        log.info("Processando débitos automáticos (rotina diária)...");
        process();
    }

    private void process() {
        try {
            MDC.put("idRequisicao", "debito-automatico-" + UUID.randomUUID());
            LocalDate hoje = LocalDate.now();
            List<Long> espacos = repository.findEspacosComDebitoAutomaticoVencido(hoje);
            for (Long espacoId : espacos) {
                List<Transacao> pendentes = repository.findDebitoAutomaticoAQuitar(espacoId, hoje);
                for (Transacao t : pendentes) {
                    transacaoService.quitarDebitoAutomatico(t);
                    log.info("Débito automático quitado: id={} conta={} valor={}",
                            t.getId(), t.getConta().getId(), t.getValor());
                }
            }
        } finally {
            MDC.clear();
        }
    }
}
