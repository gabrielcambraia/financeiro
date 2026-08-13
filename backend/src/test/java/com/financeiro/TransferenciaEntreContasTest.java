package com.financeiro;

import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.DirecaoTransferencia;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o comportamento de transferência entre contas em
 * {@code TransacaoService} (create/update/delete/pagar/estornar/cancelar) —
 * o maior risco de regressão do diff que introduziu esse fluxo. Uma
 * transferência é sempre duas linhas (SAIDA/ENTRADA) com o mesmo
 * {@code transferenciaId}, tratadas em par em toda a operação.
 */
class TransferenciaEntreContasTest extends TesteIntegracaoBase {

    @Test
    void criar_geraDuasPernas_moveSaldoDasDuasContas() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        List<TransacaoDTO> pernas = criarTransferencia(token, origem, destino, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));

        assertThat(pernas).hasSize(2);
        TransacaoDTO saida = pernaPorDirecao(pernas, DirecaoTransferencia.SAIDA);
        TransacaoDTO entrada = pernaPorDirecao(pernas, DirecaoTransferencia.ENTRADA);
        assertThat(saida.getTransferenciaId()).isEqualTo(entrada.getTransferenciaId());
        assertThat(saida.getCategoriaId()).isNull();
        assertThat(entrada.getCategoriaId()).isNull();

        assertThat(saldoConta(token, origem)).isEqualByComparingTo("70");
        assertThat(saldoConta(token, destino)).isEqualByComparingTo("80");
    }

    @Test
    void criar_dataFutura_naoAjustaSaldo_pagarDepoisAjustaAsDuas() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        List<TransacaoDTO> pernas = criarTransferencia(token, origem, destino, BigDecimal.valueOf(30), LocalDate.now().plusDays(5));
        assertThat(saldoConta(token, origem)).isEqualByComparingTo("100");
        assertThat(saldoConta(token, destino)).isEqualByComparingTo("50");

        TransacaoDTO saida = pernaPorDirecao(pernas, DirecaoTransferencia.SAIDA);
        pagar(token, saida.getId());

        assertThat(saldoConta(token, origem)).isEqualByComparingTo("70");
        assertThat(saldoConta(token, destino)).isEqualByComparingTo("80");
    }

    @Test
    void estornar_reverteAsDuasPernas_zeraDataPagamento() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        List<TransacaoDTO> pernas = criarTransferencia(token, origem, destino, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));
        TransacaoDTO saida = pernaPorDirecao(pernas, DirecaoTransferencia.SAIDA);

        TransacaoDTO resultado = estornar(token, saida.getId());

        assertThat(saldoConta(token, origem)).isEqualByComparingTo("100");
        assertThat(saldoConta(token, destino)).isEqualByComparingTo("50");
        assertThat(resultado.getDataPagamento()).isNull();
        TransacaoDTO entradaAtualizada = buscarTransacaoNoMes(token, pernaPorDirecao(pernas, DirecaoTransferencia.ENTRADA).getId());
        assertThat(entradaAtualizada.getDataPagamento()).isNull();
    }

    @Test
    void cancelar_cancelaAsDuasPernas_reverteSaldo_recursaoTermina() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        List<TransacaoDTO> pernas = criarTransferencia(token, origem, destino, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));
        TransacaoDTO saida = pernaPorDirecao(pernas, DirecaoTransferencia.SAIDA);

        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + saida.getId() + "/cancelar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(saldoConta(token, origem)).isEqualByComparingTo("100");
        assertThat(saldoConta(token, destino)).isEqualByComparingTo("50");

        TransacaoDTO entradaAtualizada = buscarTransacaoNoMes(token, pernaPorDirecao(pernas, DirecaoTransferencia.ENTRADA).getId());
        assertThat(entradaAtualizada.getDataCancelamento()).isNotNull();
    }

    @Test
    void delete_deUmaPerna_apagaOParInteiro_reverteSaldo_ignorandoScope() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        List<TransacaoDTO> pernas = criarTransferencia(token, origem, destino, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));
        TransacaoDTO saida = pernaPorDirecao(pernas, DirecaoTransferencia.SAIDA);

        patch("/api/transacoes/" + saida.getId() + "/cancelar?scope=UNICA", null, token, TransacaoDTO.class);

        ResponseEntity<Void> resposta = delete("/api/transacoes/" + saida.getId() + "?scope=UNICA", token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(saldoConta(token, origem)).isEqualByComparingTo("100");
        assertThat(saldoConta(token, destino)).isEqualByComparingTo("50");
        assertThat(transacoesDoMes(token, LocalDate.now()))
                .noneMatch(t -> saida.getTransferenciaId().equals(t.get("transferenciaId")));
    }

    @Test
    void update_alteraValorDescricaoData_nasDuasPernas_recalculaSaldo() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        List<TransacaoDTO> pernas = criarTransferencia(token, origem, destino, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));
        TransacaoDTO saida = pernaPorDirecao(pernas, DirecaoTransferencia.SAIDA);

        TransacaoDTO alteracao = new TransacaoDTO();
        alteracao.setContaId(origem);
        alteracao.setTipo(TipoTransacao.TRANSFERENCIA);
        alteracao.setTipoPagamento(TipoPagamento.DEBITO);
        alteracao.setValor(BigDecimal.valueOf(50));
        alteracao.setDescricao("transferência atualizada");
        alteracao.setData(LocalDate.now().minusDays(1));
        alteracao.setDataPagamento(LocalDate.now().minusDays(1));

        ResponseEntity<TransacaoDTO> resposta = put("/api/transacoes/" + saida.getId(), alteracao, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        // saldo recalculado com o novo valor (50 em vez de 30)
        assertThat(saldoConta(token, origem)).isEqualByComparingTo("50");
        assertThat(saldoConta(token, destino)).isEqualByComparingTo("100");

        TransacaoDTO entradaAtualizada = buscarTransacaoNoMes(token, pernaPorDirecao(pernas, DirecaoTransferencia.ENTRADA).getId());
        assertThat(entradaAtualizada.getDescricao()).isEqualTo("transferência atualizada");
        assertThat(entradaAtualizada.getValor()).isEqualByComparingTo("50");
    }

    @Test
    void criar_destinoAusente_400() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO dto = transferencia(origem, null, BigDecimal.valueOf(30), LocalDate.now());
        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/transacoes", dto, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Conta destino é obrigatória para transferência");
    }

    @Test
    void criar_destinoIgualOrigem_400() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO dto = transferencia(origem, origem, BigDecimal.valueOf(30), LocalDate.now());
        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/transacoes", dto, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Conta destino deve ser diferente da conta origem");
    }

    @Test
    void criar_comTotalParcelasOuFixa_continuaGerandoApenasDuasTransacoes() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        TransacaoDTO comParcelas = transferencia(origem, destino, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));
        comParcelas.setTotalParcelas(3);
        List<TransacaoDTO> resultadoParcelas = criarTransacao(token, comParcelas);
        assertThat(resultadoParcelas).hasSize(2);

        TransacaoDTO fixa = transferencia(origem, destino, BigDecimal.valueOf(10), LocalDate.now().minusDays(1));
        fixa.setFixa(true);
        List<TransacaoDTO> resultadoFixa = criarTransacao(token, fixa);
        assertThat(resultadoFixa).hasSize(2);
    }

    @Test
    void toDTO_preenchContaVinculada_comContaDoOutroLado() {
        String token = registrar();
        Long origem = criarConta(token, BigDecimal.valueOf(100));
        Long destino = criarConta(token, BigDecimal.valueOf(50));

        List<TransacaoDTO> pernas = criarTransferencia(token, origem, destino, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));
        TransacaoDTO saida = pernaPorDirecao(pernas, DirecaoTransferencia.SAIDA);
        TransacaoDTO entrada = pernaPorDirecao(pernas, DirecaoTransferencia.ENTRADA);

        assertThat(saida.getContaVinculada()).isNotNull();
        assertThat(saida.getContaVinculada().getId()).isEqualTo(destino);
        assertThat(entrada.getContaVinculada()).isNotNull();
        assertThat(entrada.getContaVinculada().getId()).isEqualTo(origem);
    }

    private TransacaoDTO transferencia(Long origem, Long destino, BigDecimal valor, LocalDate data) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(origem);
        dto.setContaDestinoId(destino);
        dto.setTipo(TipoTransacao.TRANSFERENCIA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setDescricao("transferência teste");
        dto.setData(data);
        dto.setFixa(false);
        return dto;
    }

    private List<TransacaoDTO> criarTransferencia(String token, Long origem, Long destino, BigDecimal valor, LocalDate data) {
        return criarTransacao(token, transferencia(origem, destino, valor, data));
    }

    private List<TransacaoDTO> criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = post("/api/transacoes", dto, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private TransacaoDTO pernaPorDirecao(List<TransacaoDTO> pernas, DirecaoTransferencia direcao) {
        return pernas.stream().filter(p -> p.getDirecaoTransferencia() == direcao).findFirst().orElseThrow();
    }

    private void pagar(String token, Long id) {
        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + id + "/pagar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TransacaoDTO estornar(String token, Long id) {
        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + id + "/estornar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> transacoesDoMes(String token, LocalDate dia) {
        ResponseEntity<List<Map>> resposta = get("/api/transacoes?month=" + java.time.YearMonth.from(dia), token,
                new ParameterizedTypeReference<List<Map>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private TransacaoDTO buscarTransacaoNoMes(String token, Long id) {
        ResponseEntity<List<TransacaoDTO>> resposta = get("/api/transacoes?month=" + java.time.YearMonth.now(), token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
    }
}
