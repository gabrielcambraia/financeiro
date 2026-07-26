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
        // parcela do mês -1 e a do mês atual já venceram (dataBase = -1 mês)
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("80");

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
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("80");

        // apaga a partir da 2ª parcela (mês atual) em diante
        TransacaoDTO segunda = parcelas.stream().filter(p -> p.getNumeroParcela() == 2).findFirst().orElseThrow();
        deletarTransacao(token, segunda.getId(), "FUTURAS");

        // reverte só a parcela vencida (mês atual); a do mês -1 permanece
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("90");
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
        ResponseEntity<TransacaoDTO> resposta = put("/api/transacoes/" + id, dto, token, TransacaoDTO.class);
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
