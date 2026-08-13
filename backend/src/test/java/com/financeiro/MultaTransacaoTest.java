package com.financeiro;

import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a multa por atraso de pagamento (informada manualmente em
 * {@code PATCH /transacoes/{id}/pagar}) e o bloqueio de RECEITA em crédito
 * (não faz sentido — receita em crédito não existe). Ver TransacaoService.
 */
class MultaTransacaoTest extends TesteIntegracaoBase {

    @Test
    void pagar_comMulta_debitaValorMaisMulta_estornarDevolveEZeraMulta() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO dto = despesaPendente(contaId, BigDecimal.valueOf(100));
        Long id = criarTransacao(token, dto).get(0).getId();

        ResponseEntity<TransacaoDTO> respostaPagar = patch("/api/transacoes/" + id + "/pagar",
                Map.of("multa", "10"), token, TransacaoDTO.class);
        assertThat(respostaPagar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaPagar.getBody().getMulta()).isEqualByComparingTo("10");

        // 1000 - (100 + 10) = 890
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("890");

        ResponseEntity<TransacaoDTO> respostaEstornar = patch("/api/transacoes/" + id + "/estornar",
                null, token, TransacaoDTO.class);
        assertThat(respostaEstornar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaEstornar.getBody().getMulta()).isNull();
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000");
    }

    @Test
    void pagar_semMulta_naoAlteraComportamentoExistente() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO dto = despesaPendente(contaId, BigDecimal.valueOf(100));
        Long id = criarTransacao(token, dto).get(0).getId();

        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + id + "/pagar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().getMulta()).isNull();
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("900");
    }

    @Test
    void cancelar_transacaoPagaComMulta_reverteValorMaisMultaEZeraMulta() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO dto = despesaPendente(contaId, BigDecimal.valueOf(100));
        Long id = criarTransacao(token, dto).get(0).getId();
        patch("/api/transacoes/" + id + "/pagar", Map.of("multa", "20"), token, TransacaoDTO.class);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("880");

        ResponseEntity<TransacaoDTO> respostaCancelar = restTemplate.exchange(
                url("/api/transacoes/" + id + "/cancelar?scope=UNICA"), HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(autenticado(token)), TransacaoDTO.class);
        assertThat(respostaCancelar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaCancelar.getBody().getMulta()).isNull();
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000");
    }

    @Test
    void criar_receitaEmCredito_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setTipo(TipoTransacao.RECEITA);
        dto.setTipoPagamento(TipoPagamento.CREDITO);
        dto.setValor(BigDecimal.valueOf(50));
        dto.setData(LocalDate.now());
        dto.setFixa(false);

        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/transacoes", dto, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Receita não pode ser em crédito");
    }

    @Test
    void update_receitaEmCredito_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO criacao = new TransacaoDTO();
        criacao.setContaId(contaId);
        criacao.setTipo(TipoTransacao.RECEITA);
        criacao.setTipoPagamento(TipoPagamento.DEBITO);
        criacao.setValor(BigDecimal.valueOf(50));
        criacao.setData(LocalDate.now());
        criacao.setFixa(false);
        Long id = criarTransacao(token, criacao).get(0).getId();

        TransacaoDTO alteracao = new TransacaoDTO();
        alteracao.setContaId(contaId);
        alteracao.setTipo(TipoTransacao.RECEITA);
        alteracao.setTipoPagamento(TipoPagamento.CREDITO);
        alteracao.setValor(BigDecimal.valueOf(50));
        alteracao.setData(LocalDate.now());
        alteracao.setFixa(false);

        ResponseEntity<Map> resposta = restTemplate.exchange(
                url("/api/transacoes/" + id), HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(alteracao, autenticado(token)), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Receita não pode ser em crédito");
    }

    @Test
    void itensFatura_semCartaoId_filtraPorContaDePagamento() {
        String token = registrar();
        Long contaA = criarConta(token, BigDecimal.valueOf(1000));
        Long contaB = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoA = criarCartao(token, contaA);
        Long cartaoB = criarCartao(token, contaB);

        criarItem(token, cartaoA, BigDecimal.valueOf(100));
        criarItem(token, cartaoB, BigDecimal.valueOf(200));

        List<ItemFaturaDTO> doresContaA = listarItensPorConta(token, contaA);
        assertThat(doresContaA).hasSize(1);
        assertThat(doresContaA.get(0).getContaPagamentoId()).isEqualTo(contaA);
        assertThat(doresContaA.get(0).getValor()).isEqualByComparingTo("100");

        List<ItemFaturaDTO> doresContaB = listarItensPorConta(token, contaB);
        assertThat(doresContaB).hasSize(1);
        assertThat(doresContaB.get(0).getValor()).isEqualByComparingTo("200");

        List<ItemFaturaDTO> todos = listarItensPorConta(token, null);
        assertThat(todos).hasSize(2);
    }

    // ---------- helpers ----------

    private TransacaoDTO despesaPendente(Long contaId, BigDecimal valor) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setDescricao("teste multa");
        dto.setData(LocalDate.now());
        dto.setFixa(false);
        dto.setQuitarNaCriacao(false);
        return dto;
    }

    private List<TransacaoDTO> criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = post("/api/transacoes", dto, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private Long criarCartao(String token, Long contaPagamentoId) {
        com.financeiro.dto.CartaoDTO dto = new com.financeiro.dto.CartaoDTO();
        dto.setNome("Cartão Teste");
        dto.setLimite(BigDecimal.valueOf(500));
        dto.setDiaFechamento(28);
        dto.setDiaVencimento(5);
        dto.setContaPagamentoId(contaPagamentoId);
        dto.setCor("#000000");
        dto.setIcone("credit-card");
        ResponseEntity<com.financeiro.dto.CartaoDTO> resposta = post("/api/cartoes", dto, token, com.financeiro.dto.CartaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    private void criarItem(String token, Long cartaoId, BigDecimal valor) {
        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setCartaoId(cartaoId);
        dto.setValor(valor);
        dto.setDescricao("compra teste");
        dto.setData(LocalDate.now());
        ResponseEntity<List<ItemFaturaDTO>> resposta = post("/api/itens-fatura", dto, token,
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private List<ItemFaturaDTO> listarItensPorConta(String token, Long contaId) {
        java.time.YearMonth mes = java.time.YearMonth.now();
        String caminho = contaId != null
                ? "/api/itens-fatura?month=" + mes + "&contaId=" + contaId
                : "/api/itens-fatura?month=" + mes;
        ResponseEntity<List<ItemFaturaDTO>> resposta = get(caminho, token,
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }
}
