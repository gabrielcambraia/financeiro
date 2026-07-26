package com.financeiro;

import com.financeiro.entity.Conta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorTransacaoFixa;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o comportamento de {@link AgendadorTransacaoFixa}: a quitação de
 * transações é manual (ver {@code TransacaoService.pagar}) — o agendador não
 * ajusta mais saldo sozinho quando uma data é alcançada, ele só garante a
 * extensão da janela de 12 meses para transações fixas. Semeia estados que
 * a API nunca produz sozinha (transação passada ainda não paga) direto no
 * repositório para travar que o agendador não mexe nelas.
 */
class AgendadorTransacaoFixaTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorTransacaoFixa agendador;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Test
    void agendador_naoMaturaVencida_saldoIntacto() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        Transacao t = seedTransacao(u, LocalDate.now().minusDays(1), BigDecimal.valueOf(30), false, false);

        agendador.onStartup();

        assertThat(saldoAtual(u.contaId())).isEqualByComparingTo("100");
        assertThat(transacaoRepository.findById(t.getId()).orElseThrow().isSaldoAjustado()).isFalse();
    }

    @Test
    void maturacao_naoTocaFutura() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        seedTransacao(u, LocalDate.now().plusMonths(1), BigDecimal.valueOf(30), false, false);

        agendador.onStartup();

        assertThat(saldoAtual(u.contaId())).isEqualByComparingTo("100");
    }

    @Test
    void extensao_criaEntradasFixasFaltantes() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        seedTransacao(u, LocalDate.now(), BigDecimal.valueOf(20), true, true);

        agendador.onStartup();

        YearMonth mesAtual = YearMonth.now();
        for (int i = 1; i <= 12; i++) {
            long total = contarTransacoesDoMes(u.espacoId(), mesAtual.plusMonths(i));
            assertThat(total).as("mês +%d", i).isEqualTo(1);
        }
    }

    @Test
    void extensao_idempotente_naoDuplica() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        seedTransacao(u, LocalDate.now(), BigDecimal.valueOf(20), true, true);

        agendador.onStartup();
        long totalAposPrimeiraRodada = transacaoRepository.findAll().stream()
                .filter(t -> u.espacoId().equals(t.getEspacoId())).count();

        agendador.onFirstOfMonth();
        long totalAposSegundaRodada = transacaoRepository.findAll().stream()
                .filter(t -> u.espacoId().equals(t.getEspacoId())).count();

        assertThat(totalAposSegundaRodada).isEqualTo(totalAposPrimeiraRodada);
    }

    @Test
    void agendador_naoMatura_multiplosEspacos() {
        Usuario a = registrarComConta(BigDecimal.valueOf(100));
        Usuario b = registrarComConta(BigDecimal.valueOf(200));

        seedTransacao(a, LocalDate.now().minusDays(1), BigDecimal.valueOf(10), false, false);
        seedTransacao(b, LocalDate.now().minusDays(1), BigDecimal.valueOf(50), false, false);

        agendador.onStartup();

        assertThat(saldoAtual(a.contaId())).isEqualByComparingTo("100");
        assertThat(saldoAtual(b.contaId())).isEqualByComparingTo("200");
    }

    private Transacao seedTransacao(Usuario u, LocalDate data, BigDecimal valor, boolean fixa, boolean saldoAjustado) {
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        Transacao t = Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(valor)
                .descricao("seed")
                .data(data)
                .fixa(fixa)
                .saldoAjustado(saldoAjustado)
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .build();
        return transacaoRepository.save(t);
    }

    private BigDecimal saldoAtual(Long contaId) {
        return contaRepository.findById(contaId).orElseThrow().getSaldo();
    }

    private long contarTransacoesDoMes(Long espacoId, YearMonth mes) {
        return transacaoRepository.findByEspacoIdAndDataBetweenOrderByDataDesc(
                espacoId, mes.atDay(1), mes.atEndOfMonth()).size();
    }
}
