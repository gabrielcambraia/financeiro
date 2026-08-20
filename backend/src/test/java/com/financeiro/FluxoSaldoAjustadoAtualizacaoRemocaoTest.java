package com.financeiro;

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
 * Trava o comportamento de {@code TransacaoService.update()} (reversão do
 * saldo antigo + reaplicação conforme a nova data) e dos três scopes de
 * {@code TransacaoService.delete()}. Ver regra em CLAUDE.md "Lógica de
 * saldo (balance_adjusted)".
 */
class FluxoSaldoAjustadoAtualizacaoRemocaoTest extends TesteIntegracaoBase {

    // ---------- update() ----------

    @Test
    void update_comDataPagamento_aplicaSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO criada = criarTransacao(token,
                transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(40), LocalDate.now().plusMonths(1))).get(0);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");

        TransacaoDTO alteracao = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(40), LocalDate.now().minusDays(1));
        alteracao.setDataPagamento(LocalDate.now().minusDays(1));
        atualizarTransacao(token, criada.getId(), alteracao);

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("60");
    }

    @Test
    void update_movePassadoParaFuturo_reverteSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO criada = criarTransacao(token,
                transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(40), LocalDate.now().minusDays(1))).get(0);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("60");

        TransacaoDTO alteracao = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(40), LocalDate.now().plusMonths(2));
        atualizarTransacao(token, criada.getId(), alteracao);

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");
    }

    @Test
    void update_alteraValor_dataPassada_reverteEReaplica() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO criada = criarTransacao(token,
                transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(50), LocalDate.now().minusDays(1))).get(0);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("50");

        TransacaoDTO alteracao = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(80), LocalDate.now().minusDays(1));
        alteracao.setDataPagamento(LocalDate.now().minusDays(1));
        atualizarTransacao(token, criada.getId(), alteracao);

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("20");
    }

    @Test
    void update_trocaConta_reverteNaAntigaAplicaNaNova() {
        String token = registrar();
        Long contaA = criarConta(token, BigDecimal.valueOf(100));
        Long contaB = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO criada = criarTransacao(token,
                transacao(contaA, TipoTransacao.DESPESA, BigDecimal.valueOf(30), LocalDate.now().minusDays(1))).get(0);
        assertThat(saldoConta(token, contaA)).isEqualByComparingTo("70");
        assertThat(saldoConta(token, contaB)).isEqualByComparingTo("100");

        TransacaoDTO alteracao = transacao(contaB, TipoTransacao.DESPESA, BigDecimal.valueOf(30), LocalDate.now().minusDays(1));
        alteracao.setDataPagamento(LocalDate.now().minusDays(1));
        atualizarTransacao(token, criada.getId(), alteracao);

        assertThat(saldoConta(token, contaA)).isEqualByComparingTo("100");
        assertThat(saldoConta(token, contaB)).isEqualByComparingTo("70");
    }

    @Test
    void update_trocaTipo_receitaParaDespesa_ajustaSinal() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO criada = criarTransacao(token,
                transacao(contaId, TipoTransacao.RECEITA, BigDecimal.valueOf(20), LocalDate.now().minusDays(1))).get(0);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("120");

        TransacaoDTO alteracao = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(20), LocalDate.now().minusDays(1));
        alteracao.setDataPagamento(LocalDate.now().minusDays(1));
        atualizarTransacao(token, criada.getId(), alteracao);

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("80");
    }

    // ---------- delete() ----------

    @Test
    void delete_unica_ajustada_reverteSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO criada = criarTransacao(token,
                transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(40), LocalDate.now().minusDays(1))).get(0);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("60");

        cancelarTransacao(token, criada.getId(), "UNICA");
        deletarTransacao(token, criada.getId(), "UNICA");

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");
    }

    @Test
    void delete_unica_naoAjustada_naoMexeSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO criada = criarTransacao(token,
                transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(40), LocalDate.now().plusMonths(1))).get(0);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");

        cancelarTransacao(token, criada.getId(), "UNICA");
        deletarTransacao(token, criada.getId(), "UNICA");

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");
    }

    @Test
    void delete_grupo_reverteApenasAjustadas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO dto = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(10), LocalDate.now().minusMonths(1));
        dto.setTotalParcelas(3);
        List<TransacaoDTO> parcelas = criarTransacao(token, dto);
        assertThat(parcelas).hasSize(3);
        // valor total (10) dividido em 3 parcelas (3.33 + 3.33 + 3.34); parcela
        // do mês -1 e a do mês atual já venceram (dataBase = -1 mês) = 3.33 + 3.33
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("93.34");

        cancelarTransacao(token, parcelas.get(0).getId(), "GRUPO");
        deletarTransacao(token, parcelas.get(0).getId(), "GRUPO");

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");
        assertThat(transacoesDoGrupo(token, parcelas.get(0).getGrupoParcelaId())).isEmpty();
    }

    @Test
    void delete_futuras_parcelas_deDataEmDiante() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO dto = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(10), LocalDate.now().minusMonths(1));
        dto.setTotalParcelas(3);
        List<TransacaoDTO> parcelas = criarTransacao(token, dto);
        // valor total (10) dividido em 3 parcelas (3.33 + 3.33 + 3.34); só as
        // dos meses -1 e atual já venceram = 3.33 + 3.33
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("93.34");

        // apaga a partir da 2ª parcela (mês atual) em diante
        TransacaoDTO segunda = parcelas.stream().filter(p -> p.getNumeroParcela() == 2).findFirst().orElseThrow();
        cancelarTransacao(token, segunda.getId(), "FUTURAS");
        deletarTransacao(token, segunda.getId(), "FUTURAS");

        // reverte só a parcela vencida (mês atual, 3.33); a do mês -1 permanece
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("96.67");
        assertThat(transacoesDoGrupo(token, parcelas.get(0).getGrupoParcelaId())).hasSize(1);
    }

    @Test
    void delete_futuras_fixa_deDataEmDiante() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO dto = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(15), LocalDate.now());
        dto.setFixa(true);
        // create() só devolve a entrada do mês atual na resposta; as 11 futuras
        // são persistidas mas não retornadas (ver TransacaoService.create()).
        List<TransacaoDTO> criadas = criarTransacao(token, dto);
        assertThat(criadas).hasSize(1);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("85");

        cancelarTransacao(token, criadas.get(0).getId(), "FUTURAS");
        deletarTransacao(token, criadas.get(0).getId(), "FUTURAS");

        // única entrada ajustada era a do mês atual, que também foi apagada
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");
    }

    // --- testes de série de fixas (origemFixaId / serieAtiva) ---

    @Test
    void delete_futuras_fixa_naoAfetaOutraSerie() {
        // Regressão do bug: delete FUTURAS de uma série não pode tocar outras séries.
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(200));

        // Série A: Aluguel R$50
        TransacaoDTO dtoA = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(50), LocalDate.now());
        dtoA.setFixa(true);
        List<TransacaoDTO> serieA = criarTransacao(token, dtoA);

        // Série B: Netflix R$30
        TransacaoDTO dtoB = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(30), LocalDate.now());
        dtoB.setFixa(true);
        criarTransacao(token, dtoB);

        // Cancela e apaga série A (mês corrente)
        cancelarTransacao(token, serieA.get(0).getId(), "FUTURAS");
        deletarTransacao(token, serieA.get(0).getId(), "FUTURAS");

        // Série B deve continuar íntegra no mês corrente com exatamente 1 lançamento de R$30
        String mesAtualStr = YearMonth.now().toString();
        ResponseEntity<List<Map>> resp = get("/api/transacoes?month=" + mesAtualStr, token,
                new ParameterizedTypeReference<List<Map>>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map> txsMesAtual = resp.getBody();
        long contB = txsMesAtual.stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 30.0)
                .count();
        assertThat(contB).as("série Netflix deve ter 1 lançamento no mês corrente").isEqualTo(1);
        long contA = txsMesAtual.stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 50.0)
                .count();
        assertThat(contA).as("série Aluguel deve ter sido apagada do mês corrente").isEqualTo(0);
    }

    @Test
    void update_futuras_parcelas_propagaValorEDeltaData() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        // Cria parcelado em 3x começando no próximo mês (todas futuras, não afetam saldo)
        LocalDate inicio = LocalDate.now().plusMonths(1).withDayOfMonth(10);
        TransacaoDTO dto = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(300), inicio);
        dto.setTotalParcelas(3);
        List<TransacaoDTO> parcelas = criarTransacao(token, dto);
        assertThat(parcelas).hasSize(3);

        // Edita a 2ª parcela com escopo FUTURAS: novo valor por parcela = 120
        Long id2 = parcelas.get(1).getId();
        TransacaoDTO edicao = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(120), inicio.plusMonths(1));
        atualizarTransacao(token, id2, edicao, "FUTURAS");

        // 2ª e 3ª parcelas devem ter o novo valor; 1ª permanece original (100)
        String mes1 = YearMonth.from(inicio).toString();
        String mes2 = YearMonth.from(inicio.plusMonths(1)).toString();
        String mes3 = YearMonth.from(inicio.plusMonths(2)).toString();

        ResponseEntity<List<Map>> r1 = get("/api/transacoes?month=" + mes1, token,
                new ParameterizedTypeReference<List<Map>>() {});
        double valor1 = Double.parseDouble(r1.getBody().stream()
                .filter(tx -> parcelas.get(0).getId().equals(((Number) tx.get("id")).longValue()))
                .findFirst().orElseThrow().get("valor").toString());
        assertThat(valor1).isEqualTo(100.0);

        ResponseEntity<List<Map>> r2 = get("/api/transacoes?month=" + mes2, token,
                new ParameterizedTypeReference<List<Map>>() {});
        double valor2 = Double.parseDouble(r2.getBody().stream()
                .filter(tx -> ((Number) tx.get("id")).longValue() == id2)
                .findFirst().orElseThrow().get("valor").toString());
        assertThat(valor2).isEqualTo(120.0);

        ResponseEntity<List<Map>> r3 = get("/api/transacoes?month=" + mes3, token,
                new ParameterizedTypeReference<List<Map>>() {});
        double valor3 = Double.parseDouble(r3.getBody().stream()
                .filter(tx -> parcelas.get(2).getId().equals(((Number) tx.get("id")).longValue()))
                .findFirst().orElseThrow().get("valor").toString());
        assertThat(valor3).isEqualTo(120.0);
    }

    @Test
    void delete_futuras_fixa_bloqueiaComPagaNoEscopo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        // Cria fixa no mês corrente (saldoAjustado=true; não cancelada)
        TransacaoDTO dto = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(20), LocalDate.now());
        dto.setFixa(true);
        List<TransacaoDTO> criadas = criarTransacao(token, dto);
        assertThat(criadas).hasSize(1);

        // Tenta deletar FUTURAS sem cancelar primeiro — deve ser bloqueado (400)
        // pois verificarCanceladaParaExclusao exige cancelamento antes da exclusão
        ResponseEntity<Map> erroResp = deleteComCorpo(
                "/api/transacoes/" + criadas.get(0).getId() + "?scope=FUTURAS", token);
        assertThat(erroResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private TransacaoDTO transacao(Long contaId, TipoTransacao tipo, BigDecimal valor, LocalDate data) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setTipo(tipo);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setDescricao("teste");
        dto.setData(data);
        dto.setFixa(false);
        return dto;
    }

    private List<TransacaoDTO> criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = post("/api/transacoes", dto, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private void atualizarTransacao(String token, Long id, TransacaoDTO dto) {
        atualizarTransacao(token, id, dto, "UNICA");
    }

    private void atualizarTransacao(String token, Long id, TransacaoDTO dto, String scope) {
        ResponseEntity<TransacaoDTO> resposta = put("/api/transacoes/" + id + "?scope=" + scope, dto, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void cancelarTransacao(String token, Long id, String scope) {
        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + id + "/cancelar?scope=" + scope, null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void deletarTransacao(String token, Long id, String scope) {
        ResponseEntity<Void> resposta = delete("/api/transacoes/" + id + "?scope=" + scope, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    private List<Map> transacoesDoGrupo(String token, String grupoParcelaId) {
        // não há endpoint dedicado por grupo; varre os últimos 4 meses (cobre qualquer cenário de teste)
        List<Map> resultado = new java.util.ArrayList<>();
        java.time.YearMonth mes = java.time.YearMonth.now().minusMonths(2);
        for (int i = 0; i < 5; i++) {
            ResponseEntity<List<Map>> resposta = get("/api/transacoes?month=" + mes.plusMonths(i), token,
                    new ParameterizedTypeReference<List<Map>>() {
                    });
            resposta.getBody().stream()
                    .filter(t -> grupoParcelaId.equals(t.get("grupoParcelaId")))
                    .forEach(resultado::add);
        }
        return resultado;
    }
}
