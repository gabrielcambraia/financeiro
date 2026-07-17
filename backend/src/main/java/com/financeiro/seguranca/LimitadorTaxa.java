package com.financeiro.seguranca;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador de taxa em memória (janela fixa) por chave arbitrária — usado
 * para conter força bruta em {@code /api/auth/login} e {@code /register}.
 * Suficiente para uma instância única; não compartilha estado entre réplicas.
 */
@Component
public class LimitadorTaxa {

    private static final int LIMITE_REQUISICOES = 10;
    private static final long JANELA_SEGUNDOS = 60;

    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();

    public boolean permitir(String chave) {
        Janela janela = janelas.computeIfAbsent(chave, k -> new Janela());
        return janela.registrarTentativa();
    }

    // Evita crescimento indefinido do mapa: remove janelas inativas há mais
    // de 10x o período de janela.
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void limpar() {
        Instant limite = Instant.now().minusSeconds(JANELA_SEGUNDOS * 10);
        janelas.entrySet().removeIf(e -> e.getValue().inicio.isBefore(limite));
    }

    private static final class Janela {
        private volatile Instant inicio = Instant.now();
        private final AtomicInteger contagem = new AtomicInteger(0);

        synchronized boolean registrarTentativa() {
            Instant agora = Instant.now();
            if (inicio.plusSeconds(JANELA_SEGUNDOS).isBefore(agora)) {
                inicio = agora;
                contagem.set(0);
            }
            return contagem.incrementAndGet() <= LIMITE_REQUISICOES;
        }
    }
}
