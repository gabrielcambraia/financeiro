package com.financeiro;

import com.financeiro.entity.Conta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorDebitoAutomatico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o comportamento de {@link AgendadorDebitoAutomatico}: despesas marcadas
 * como débito automático devem ser quitadas automaticamente quando o
 * {@code dataVencimento} é alcançado, com ajuste imediato do saldo da conta.
 *
 * Os estados são semeados diretamente no repositório para simular cenários que
 * a API não produziria sozinha (ex.: vencimento no passado ainda pendente, como
 * acontece quando o servidor ficou fora do ar por dias).
 */
class AgendadorDebitoAutomaticoTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorDebitoAutomatico agendador;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Test
    void vencimentoHoje_quitaEAjustaSaldo() {
        Usuario u = registrarComConta(BigDecimal.valueOf(500));
        Transacao t = seedPendente(u, LocalDate.now(), BigDecimal.valueOf(100));

        agendador.onStartup();

        Transacao resultado = transacaoRepository.findById(t.getId()).orElseThrow();
        assertThat(resultado.isSaldoAjustado()).isTrue();
        assertThat(resultado.getDataPagamento()).isEqualTo(LocalDate.now());
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("400");
    }

    @Test
    void vencimentoAtrasado_tambemQuita() {
        Usuario u = registrarComConta(BigDecimal.valueOf(200));
        Transacao t = seedPendente(u, LocalDate.now().minusDays(3), BigDecimal.valueOf(50));

        agendador.diario();

        Transacao resultado = transacaoRepository.findById(t.getId()).orElseThrow();
        assertThat(resultado.isSaldoAjustado()).isTrue();
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("150");
    }

    @Test
    void vencimentoFuturo_naoQuita() {
        Usuario u = registrarComConta(BigDecimal.valueOf(300));
        Transacao t = seedPendente(u, LocalDate.now().plusDays(1), BigDecimal.valueOf(75));

        agendador.onStartup();

        Transacao resultado = transacaoRepository.findById(t.getId()).orElseThrow();
        assertThat(resultado.isSaldoAjustado()).isFalse();
        assertThat(resultado.getDataPagamento()).isNull();
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("300");
    }

    @Test
    void transacaoJaQuitada_naoReprocessa_saldoIntacto() {
        Usuario u = registrarComConta(BigDecimal.valueOf(200));
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        // semeada já paga — saldo da conta NÃO foi decrementado pelo banco
        transacaoRepository.save(Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(BigDecimal.valueOf(80))
                .data(LocalDate.now())
                .dataVencimento(LocalDate.now())
                .debitoAutomatico(true)
                .saldoAjustado(true)
                .dataPagamento(LocalDate.now())
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .build());

        agendador.onStartup();

        // saldo da conta continua intacto — sem segundo ajuste
        assertThat(contaRepository.findById(u.contaId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("200");
    }

    @Test
    void transacaoCancelada_naoQuita() {
        Usuario u = registrarComConta(BigDecimal.valueOf(400));
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        transacaoRepository.save(Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(BigDecimal.valueOf(100))
                .data(LocalDate.now())
                .dataVencimento(LocalDate.now())
                .debitoAutomatico(true)
                .saldoAjustado(false)
                .dataCancelamento(LocalDate.now())
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .build());

        agendador.diario();

        assertThat(contaRepository.findById(u.contaId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("400");
    }

    @Test
    void receita_flagIgnoradaPelaQuery_naoQuita() {
        // A query do repositório filtra por TipoTransacao.DESPESA — uma RECEITA com
        // debitoAutomatico=true (seeda diretamente) não entra no processamento.
        Usuario u = registrarComConta(BigDecimal.valueOf(300));
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        Transacao t = transacaoRepository.save(Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.RECEITA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(BigDecimal.valueOf(50))
                .data(LocalDate.now())
                .dataVencimento(LocalDate.now())
                .debitoAutomatico(true)
                .saldoAjustado(false)
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .build());

        agendador.onStartup();

        Transacao resultado = transacaoRepository.findById(t.getId()).orElseThrow();
        assertThat(resultado.isSaldoAjustado()).isFalse();
        assertThat(resultado.getDataPagamento()).isNull();
    }

    @Test
    void semDebitoAutomatico_naoToca() {
        Usuario u = registrarComConta(BigDecimal.valueOf(300));
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        Transacao t = transacaoRepository.save(Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(BigDecimal.valueOf(60))
                .data(LocalDate.now())
                .dataVencimento(LocalDate.now())
                .debitoAutomatico(false)
                .saldoAjustado(false)
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .build());

        agendador.diario();

        Transacao resultado = transacaoRepository.findById(t.getId()).orElseThrow();
        assertThat(resultado.isSaldoAjustado()).isFalse();
        assertThat(resultado.getDataPagamento()).isNull();
        assertThat(contaRepository.findById(u.contaId()).orElseThrow().getSaldo())
                .isEqualByComparingTo("300");
    }

    @Test
    void idempotente_duasExecucoes_saldoAjustadoUmaVez() {
        Usuario u = registrarComConta(BigDecimal.valueOf(600));
        seedPendente(u, LocalDate.now(), BigDecimal.valueOf(150));

        agendador.onStartup();
        agendador.diario();

        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("450");
    }

    @Test
    void multiplosEspacos_processaTodos() {
        Usuario a = registrarComConta(BigDecimal.valueOf(1000));
        Usuario b = registrarComConta(BigDecimal.valueOf(500));

        seedPendente(a, LocalDate.now(), BigDecimal.valueOf(200));
        seedPendente(b, LocalDate.now(), BigDecimal.valueOf(100));

        agendador.onStartup();

        assertThat(saldoConta(a.token(), a.contaId())).isEqualByComparingTo("800");
        assertThat(saldoConta(b.token(), b.contaId())).isEqualByComparingTo("400");
    }

    // ---------- helpers ----------

    private Transacao seedPendente(Usuario u, LocalDate dataVencimento, BigDecimal valor) {
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        return transacaoRepository.save(Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(valor)
                .data(dataVencimento)
                .dataVencimento(dataVencimento)
                .debitoAutomatico(true)
                .saldoAjustado(false)
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .build());
    }
}
