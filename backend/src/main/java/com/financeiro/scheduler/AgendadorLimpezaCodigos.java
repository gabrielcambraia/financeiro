package com.financeiro.scheduler;

import com.financeiro.repository.CodigoVerificacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Rede de segurança para códigos expirados que escaparam da limpeza lazy
 * (solicitar) e da deleção pós-uso (verificar). Roda diariamente às 03:00.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorLimpezaCodigos {

    private final CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limpar() {
        int removidos = codigoVerificacaoRepository.deleteByExpiraEmBefore(LocalDateTime.now().minusHours(24));
        if (removidos > 0) {
            log.info("Limpeza noturna: {} códigos expirados removidos", removidos);
        }
    }
}
