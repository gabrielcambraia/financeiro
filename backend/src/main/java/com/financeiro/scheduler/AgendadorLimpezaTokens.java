package com.financeiro.scheduler;

import com.financeiro.repository.TokenAtualizacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Apaga diariamente as linhas de {@code tokens_atualizacao} já expiradas.
 * Sem essa limpeza a tabela cresce sem limite: com o access token curto
 * (ver {@link com.financeiro.seguranca.ServicoJwt}), cada renovação insere
 * uma linha nova (ver {@link com.financeiro.seguranca.ServicoTokenAtualizacao#rotacionar}).
 * A folga de 1 dia sobre {@code expiraEm} preserva a janela de graça de
 * rotação e o rastro recente de detecção de reuso.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorLimpezaTokens {

    private static final long FOLGA_DIAS = 1;

    private final TokenAtualizacaoRepository tokenAtualizacaoRepository;

    @Scheduled(cron = "0 20 0 * * *")
    @Transactional
    public void limpar() {
        LocalDateTime limite = LocalDateTime.now().minusDays(FOLGA_DIAS);
        int removidos = tokenAtualizacaoRepository.excluirExpiradosAntesDe(limite);
        if (removidos > 0) {
            log.info("Limpeza de tokens de atualização expirados: {} linha(s) removida(s)", removidos);
        }
    }
}
