package com.financeiro;

import com.financeiro.dto.FluxoDeCaixaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code FluxoDeCaixaService} ({@code /api/fluxo-de-caixa}) e
 * {@code CalendarioService} ({@code /api/calendario}).
 */
class FluxoDeCaixaCalendarioTest extends TesteIntegracaoBase {

    @Test
    void projecao_retornaDiasMais1Pontos_comecaNoSaldoAtual() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(500));

        ResponseEntity<FluxoDeCaixaDTO> resposta = get("/api/fluxo-de-caixa?dias=10&contaId=" + contaId, token, FluxoDeCaixaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        FluxoDeCaixaDTO dto = resposta.getBody();

        assertThat(dto.getPontos()).hasSize(11);
        assertThat(dto.getSaldoAtual()).isEqualByComparingTo("500");
        assertThat(dto.getPontos().get(0).getSaldo()).isEqualByComparingTo("500");
    }

    @Test
    void pendenciaVencendoNaJanela_apareceComoDegrauNoDiaCerto() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(500));
        criarDespesaFutura(token, contaId, BigDecimal.valueOf(100), LocalDate.now().plusDays(5));

        ResponseEntity<FluxoDeCaixaDTO> resposta = get("/api/fluxo-de-caixa?dias=10&contaId=" + contaId, token, FluxoDeCaixaDTO.class);
        List<FluxoDeCaixaDTO.Ponto> pontos = resposta.getBody().getPontos();

        assertThat(pontos.get(4).getSaldo()).isEqualByComparingTo("500"); // dia 4: antes do vencimento
        assertThat(pontos.get(5).getSaldo()).isEqualByComparingTo("400"); // dia 5: vencimento, degrau
        assertThat(pontos.get(6).getSaldo()).isEqualByComparingTo("400"); // permanece após
    }

    @Test
    void vencimentoAnteriorAHoje_naoEntra_transacaoPagaOuCancelada_tambemNao() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(500));

        criarDespesaFutura(token, contaId, BigDecimal.valueOf(50), LocalDate.now().minusDays(2));

        TransacaoDTO paga = criarDespesaFutura(token, contaId, BigDecimal.valueOf(70), LocalDate.now().plusDays(3));
        pagar(token, paga.getId());

        TransacaoDTO cancelada = criarDespesaFutura(token, contaId, BigDecimal.valueOf(90), LocalDate.now().plusDays(3));
        cancelar(token, cancelada.getId());

        ResponseEntity<FluxoDeCaixaDTO> resposta = get("/api/fluxo-de-caixa?dias=10&contaId=" + contaId, token, FluxoDeCaixaDTO.class);
        List<FluxoDeCaixaDTO.Ponto> pontos = resposta.getBody().getPontos();

        for (FluxoDeCaixaDTO.Ponto ponto : pontos) {
            assertThat(ponto.getSaldo()).isEqualByComparingTo(pontos.get(0).getSaldo());
        }
    }

    @Test
    void contaId_filtraPorConta_semContaId_somaTodasContas_transferenciaSeAnula() {
        String token = registrar();
        Long contaA = criarConta(token, BigDecimal.valueOf(300));
        Long contaB = criarConta(token, BigDecimal.valueOf(200));

        criarTransferenciaFutura(token, contaA, contaB, BigDecimal.valueOf(50), LocalDate.now().plusDays(3));

        ResponseEntity<FluxoDeCaixaDTO> respostaA = get("/api/fluxo-de-caixa?dias=10&contaId=" + contaA, token, FluxoDeCaixaDTO.class);
        assertThat(respostaA.getBody().getPontos().get(3).getSaldo()).isEqualByComparingTo("250");

        ResponseEntity<FluxoDeCaixaDTO> respostaB = get("/api/fluxo-de-caixa?dias=10&contaId=" + contaB, token, FluxoDeCaixaDTO.class);
        assertThat(respostaB.getBody().getPontos().get(3).getSaldo()).isEqualByComparingTo("250");

        ResponseEntity<FluxoDeCaixaDTO> respostaTotal = get("/api/fluxo-de-caixa?dias=10", token, FluxoDeCaixaDTO.class);
        for (FluxoDeCaixaDTO.Ponto ponto : respostaTotal.getBody().getPontos()) {
            assertThat(ponto.getSaldo()).isEqualByComparingTo("500");
        }
    }

    @Test
    void simulacao_dentroDaJanela_divergeAPartirDoDia_foraDaJanela_igualANormal_semParametros_null() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(500));

        ResponseEntity<FluxoDeCaixaDTO> comSimulacaoDentro = get(
                "/api/fluxo-de-caixa?dias=10&contaId=" + contaId + "&simulacaoValor=100&simulacaoData=" + LocalDate.now().plusDays(4),
                token, FluxoDeCaixaDTO.class);
        List<FluxoDeCaixaDTO.Ponto> pontosDentro = comSimulacaoDentro.getBody().getPontos();
        assertThat(pontosDentro.get(3).getSaldoSimulado()).isEqualByComparingTo(pontosDentro.get(3).getSaldo());
        assertThat(pontosDentro.get(4).getSaldoSimulado()).isEqualByComparingTo(pontosDentro.get(4).getSaldo().subtract(BigDecimal.valueOf(100)));

        ResponseEntity<FluxoDeCaixaDTO> comSimulacaoFora = get(
                "/api/fluxo-de-caixa?dias=10&contaId=" + contaId + "&simulacaoValor=100&simulacaoData=" + LocalDate.now().plusDays(30),
                token, FluxoDeCaixaDTO.class);
        for (FluxoDeCaixaDTO.Ponto ponto : comSimulacaoFora.getBody().getPontos()) {
            assertThat(ponto.getSaldoSimulado()).isEqualByComparingTo(ponto.getSaldo());
        }

        ResponseEntity<FluxoDeCaixaDTO> semParametros = get("/api/fluxo-de-caixa?dias=10&contaId=" + contaId, token, FluxoDeCaixaDTO.class);
        assertThat(semParametros.getBody().getPontos()).allMatch(p -> p.getSaldoSimulado() == null);
    }

    @Test
    void calendario_trazTransacoesDoMesPorDataVencimento_comFallbackParaData() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(500));
        String mesAtual = YearMonth.now().toString();

        TransacaoDTO comVencimento = new TransacaoDTO();
        comVencimento.setContaId(contaId);
        comVencimento.setTipo(TipoTransacao.DESPESA);
        comVencimento.setTipoPagamento(TipoPagamento.DEBITO);
        comVencimento.setValor(BigDecimal.valueOf(20));
        comVencimento.setDescricao("com vencimento");
        comVencimento.setData(LocalDate.now());
        comVencimento.setDataVencimento(LocalDate.now());
        TransacaoDTO criada = criarTransacao(token, comVencimento);

        List<Map> calendario = calendarioDoMes(token, mesAtual);
        assertThat(calendario).anyMatch(t -> ((Number) t.get("id")).longValue() == criada.getId());
    }

    @Test
    void calendario_excluiCanceladas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(500));
        String mesAtual = YearMonth.now().toString();

        TransacaoDTO cancelada = criarDespesaFutura(token, contaId, BigDecimal.valueOf(20), LocalDate.now());
        cancelar(token, cancelada.getId());

        List<Map> calendario = calendarioDoMes(token, mesAtual);
        assertThat(calendario).noneMatch(t -> ((Number) t.get("id")).longValue() == cancelada.getId());
    }

    @Test
    void calendario_incluiFaturaDeCartaoEParcelaDeDivida_transferenciasTraz_contaVinculada() {
        String token = registrar();
        Long contaA = criarConta(token, BigDecimal.valueOf(500));
        Long contaB = criarConta(token, BigDecimal.valueOf(500));
        String mesAtual = YearMonth.now().toString();

        criarTransferenciaFutura(token, contaA, contaB, BigDecimal.valueOf(30), LocalDate.now());

        List<Map> calendario = calendarioDoMes(token, mesAtual);
        Map transferenciaSaida = calendario.stream()
                .filter(t -> "TRANSFERENCIA".equals(t.get("tipo")))
                .findFirst().orElseThrow();
        assertThat(transferenciaSaida.get("contaVinculada")).isNotNull();
    }

    // ---------- helpers ----------

    private TransacaoDTO criarDespesaFutura(String token, Long contaId, BigDecimal valor, LocalDate data) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setDescricao("despesa teste");
        dto.setData(data);
        return criarTransacao(token, dto);
    }

    private TransacaoDTO criarTransferenciaFutura(String token, Long origem, Long destino, BigDecimal valor, LocalDate data) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(origem);
        dto.setContaDestinoId(destino);
        dto.setTipo(TipoTransacao.TRANSFERENCIA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setDescricao("transferência teste");
        dto.setData(data);
        return criarTransacao(token, dto);
    }

    private TransacaoDTO criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = post("/api/transacoes", dto, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get(0);
    }

    private void pagar(String token, Long id) {
        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + id + "/pagar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void cancelar(String token, Long id) {
        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + id + "/cancelar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("rawtypes")
    private List<Map> calendarioDoMes(String token, String mes) {
        ResponseEntity<List<Map>> resposta = get("/api/calendario?mes=" + mes, token, new ParameterizedTypeReference<List<Map>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }
}
