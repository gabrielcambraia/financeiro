package com.financeiro;

import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.entity.Recorrencia;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.RecorrenciaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorRecorrencia;
import com.financeiro.service.GeradorLancamentoRecorrencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * Testa o AgendadorRecorrencia chamando onDiaUm() diretamente,
 * inserindo recorrências via repository para controlar o estado inicial
 * sem acionar a geração imediata que o endpoint POST dispararia.
 */
class AgendadorRecorrenciaTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorRecorrencia agendador;

    @SpyBean
    private GeradorLancamentoRecorrencia geradorSpy;

    @Autowired
    private RecorrenciaRepository recorrenciaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @AfterEach
    void resetarSpy() {
        Mockito.reset(geradorSpy);
    }

    private Recorrencia salvar(Long espacoId, Long usuarioId, Long contaId,
                                boolean ativa, LocalDate dataInicio, LocalDate dataFim) {
        Recorrencia r = Recorrencia.builder()
                .espacoId(espacoId)
                .usuarioId(usuarioId)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .contaId(contaId)
                .valor(BigDecimal.valueOf(300))
                .diaCompetencia(15)
                .ativa(ativa)
                .dataInicio(dataInicio)
                .dataFim(dataFim)
                .build();
        return recorrenciaRepository.save(r);
    }

    @Test
    void onDiaUm_recorrenciaAtiva_geraTransacao() {
        RespostaAutenticacao auth = registrarCompleto("Agend Ativo", "agend-ativo-" + UUID.randomUUID() + "@t.com");
        Long contaId = criarConta(auth.getToken(), BigDecimal.valueOf(1000));

        Recorrencia r = salvar(auth.getEspacoId(), auth.getUsuarioId(), contaId,
                true, YearMonth.now().atDay(1), null);

        agendador.onDiaUm();

        long geradas = transacaoRepository.findAll().stream()
                .filter(t -> r.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(geradas).isEqualTo(1);
    }

    @Test
    void onDiaUm_recorrenciaInativa_naoGeraLancamento() {
        RespostaAutenticacao auth = registrarCompleto("Agend Inativo", "agend-inativo-" + UUID.randomUUID() + "@t.com");
        Long contaId = criarConta(auth.getToken(), BigDecimal.valueOf(1000));

        Recorrencia r = salvar(auth.getEspacoId(), auth.getUsuarioId(), contaId,
                false, YearMonth.now().atDay(1), null);

        agendador.onDiaUm();

        long geradas = transacaoRepository.findAll().stream()
                .filter(t -> r.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(geradas).isZero();
    }

    @Test
    void onDiaUm_dataInicioFuturo_naoGeraLancamento() {
        RespostaAutenticacao auth = registrarCompleto("Agend Futuro", "agend-futuro-" + UUID.randomUUID() + "@t.com");
        Long contaId = criarConta(auth.getToken(), BigDecimal.valueOf(1000));

        // dataInicio no próximo mês → findAtivasParaMes não a inclui
        Recorrencia r = salvar(auth.getEspacoId(), auth.getUsuarioId(), contaId,
                true, YearMonth.now().plusMonths(1).atDay(1), null);

        agendador.onDiaUm();

        long geradas = transacaoRepository.findAll().stream()
                .filter(t -> r.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(geradas).isZero();
    }

    @Test
    void onDiaUm_dataFimNoMesPassado_naoGeraLancamento() {
        RespostaAutenticacao auth = registrarCompleto("Agend Expirado", "agend-exp-" + UUID.randomUUID() + "@t.com");
        Long contaId = criarConta(auth.getToken(), BigDecimal.valueOf(1000));

        // dataFim = último dia do mês passado → não abrange mês atual
        Recorrencia r = salvar(auth.getEspacoId(), auth.getUsuarioId(), contaId,
                true,
                YearMonth.now().minusMonths(6).atDay(1),
                YearMonth.now().minusMonths(1).atEndOfMonth());

        agendador.onDiaUm();

        long geradas = transacaoRepository.findAll().stream()
                .filter(t -> r.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(geradas).isZero();
    }

    @Test
    void onDiaUm_idempotente_naoCriaDuplicata() {
        RespostaAutenticacao auth = registrarCompleto("Agend Idem", "agend-idem-" + UUID.randomUUID() + "@t.com");
        Long contaId = criarConta(auth.getToken(), BigDecimal.valueOf(1000));

        Recorrencia r = salvar(auth.getEspacoId(), auth.getUsuarioId(), contaId,
                true, YearMonth.now().atDay(1), null);

        agendador.onDiaUm();
        agendador.onDiaUm();

        long geradas = transacaoRepository.findAll().stream()
                .filter(t -> r.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(geradas).isEqualTo(1);
    }

    @Test
    void onDiaUm_erroNaGeracao_naoQuebra_continuaProcessandoOutras() {
        RespostaAutenticacao auth = registrarCompleto("Agend Resil", "agend-resil-" + UUID.randomUUID() + "@t.com");
        Long contaId = criarConta(auth.getToken(), BigDecimal.valueOf(1000));

        Recorrencia comErro = salvar(auth.getEspacoId(), auth.getUsuarioId(), contaId,
                true, YearMonth.now().atDay(1), null);
        Recorrencia valida = salvar(auth.getEspacoId(), auth.getUsuarioId(), contaId,
                true, YearMonth.now().atDay(1), null);

        // Simula falha ao processar a primeira recorrência; a segunda deve ser processada normalmente
        doThrow(new RuntimeException("Erro simulado"))
                .when(geradorSpy).gerarParaMes(argThat(r -> comErro.getId().equals(r.getId())), any());

        assertThatNoException().isThrownBy(() -> agendador.onDiaUm());

        assertThat(transacaoRepository.findAll().stream()
                .filter(t -> comErro.getId().equals(t.getOrigemRecorrenciaId()))
                .count()).isZero();

        assertThat(transacaoRepository.findAll().stream()
                .filter(t -> valida.getId().equals(t.getOrigemRecorrenciaId()))
                .count()).isEqualTo(1);
    }
}
