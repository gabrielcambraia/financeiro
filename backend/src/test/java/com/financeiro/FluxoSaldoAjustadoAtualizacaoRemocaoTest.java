package com.financeiro;

import com.financeiro.dto.ContaDTO;
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

        // Apaga série A (mês corrente e futuros)
        deletarTransacao(token, serieA.get(0).getId(), "FUTURAS");

        // Série B deve continuar íntegra: mês+1 deve ter exatamente 1 lançamento de R$30
        String mesSeguinte = YearMonth.now().plusMonths(1).toString();
        ResponseEntity<List<Map>> resp = get("/api/transacoes?month=" + mesSeguinte, token,
                new ParameterizedTypeReference<List<Map>>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map> txsMesSeguinte = resp.getBody();
        long contB = txsMesSeguinte.stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 30.0)
                .count();
        assertThat(contB).as("série Netflix deve ter 1 lançamento no mês seguinte").isEqualTo(1);
        long contA = txsMesSeguinte.stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 50.0)
                .count();
        assertThat(contA).as("série Aluguel deve ter sido apagada do mês seguinte").isEqualTo(0);
    }

    @Test
    void update_futuras_fixa_propagaValorEDeltaData() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        // Cria fixa no mês corrente
        TransacaoDTO dto = transacao(contaId, TipoTransacao.RECEITA, BigDecimal.valueOf(1000), LocalDate.now());
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setFixa(true);
        criarTransacao(token, dto);

        // Busca o lançamento do mês seguinte para editar com escopo FUTURAS
        String mesSeguinte = YearMonth.now().plusMonths(1).toString();
        ResponseEntity<List<Map>> resp = get("/api/transacoes?month=" + mesSeguinte, token,
                new ParameterizedTypeReference<List<Map>>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map txMesSeguinte = resp.getBody().stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 1000.0)
                .findFirst().orElseThrow();
        int idMesSeguinte = (int) txMesSeguinte.get("id");
        String dataOriginalStr = txMesSeguinte.get("data").toString();
        LocalDate dataOriginal = LocalDate.parse(dataOriginalStr);

        // Edita com escopo FUTURAS: novo valor 1100, data deslocada +5 dias
        TransacaoDTO edicao = transacao(contaId, TipoTransacao.RECEITA, BigDecimal.valueOf(1100),
                dataOriginal.plusDays(5));
        edicao.setTipoPagamento(TipoPagamento.DEBITO);
        edicao.setFixa(true);
        atualizarTransacao(token, (long) idMesSeguinte, edicao, "FUTURAS");

        // Mês seguinte: deve ter R$1100 e data = original+5
        ResponseEntity<List<Map>> resp2 = get("/api/transacoes?month=" + mesSeguinte, token,
                new ParameterizedTypeReference<List<Map>>() {});
        Map txAtualizado = resp2.getBody().stream()
                .filter(tx -> (int) tx.get("id") == idMesSeguinte)
                .findFirst().orElseThrow();
        assertThat(Double.parseDouble(txAtualizado.get("valor").toString())).isEqualTo(1100.0);
        assertThat(LocalDate.parse(txAtualizado.get("data").toString()))
                .isEqualTo(dataOriginal.plusDays(5));

        // Mês +2: deve ter R$1100 e data = original+1mês+5dias (delta preservado)
        String mesDoisMeses = YearMonth.now().plusMonths(2).toString();
        ResponseEntity<List<Map>> resp3 = get("/api/transacoes?month=" + mesDoisMeses, token,
                new ParameterizedTypeReference<List<Map>>() {});
        List<Map> txsMes2 = resp3.getBody();
        long count1100 = txsMes2.stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 1100.0)
                .count();
        assertThat(count1100).as("mês +2 deve ter R$1100").isEqualTo(1);

        // Mês corrente (anterior ao ponto de edição): deve continuar R$1000
        String mesAtual = YearMonth.now().toString();
        ResponseEntity<List<Map>> resp4 = get("/api/transacoes?month=" + mesAtual, token,
                new ParameterizedTypeReference<List<Map>>() {});
        long count1000 = resp4.getBody().stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 1000.0)
                .count();
        assertThat(count1000).as("mês corrente deve permanecer R$1000").isEqualTo(1);
    }

    @Test
    void delete_futuras_fixa_bloqueiaComPagaNoEscopo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        // Cria fixa no mês corrente (paga automaticamente)
        TransacaoDTO dto = transacao(contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(20), LocalDate.now());
        dto.setFixa(true);
        List<TransacaoDTO> criadas = criarTransacao(token, dto);

        // Paga o lançamento do mês seguinte manualmente
        String mesSeguinte = YearMonth.now().plusMonths(1).toString();
        ResponseEntity<List<Map>> resp = get("/api/transacoes?month=" + mesSeguinte, token,
                new ParameterizedTypeReference<List<Map>>() {});
        Map txFutura = resp.getBody().stream()
                .filter(tx -> Double.parseDouble(tx.get("valor").toString()) == 20.0)
                .findFirst().orElseThrow();
        int idFutura = (int) txFutura.get("id");
        patch("/api/transacoes/" + idFutura + "/pagar", null, token, Map.class);

        // Tenta deletar FUTURAS a partir do mês corrente — deve ser bloqueado (400)
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

    private void deletarTransacao(String token, Long id, String scope) {
        ResponseEntity<Void> resposta = delete("/api/transacoes/" + id + "?scope=" + scope, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private BigDecimal saldoConta(String token, Long contaId) {
        ResponseEntity<List<ContaDTO>> resposta = get("/api/contas", token, new ParameterizedTypeReference<List<ContaDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream()
                .filter(c -> c.getId().equals(contaId))
                .findFirst()
                .orElseThrow()
                .getSaldo();
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
