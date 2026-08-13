package com.financeiro;

import com.financeiro.dto.DividaDTO;
import com.financeiro.dto.TransacaoDTO;
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
 * Cobre {@code DividaService} — endpoints {@code /api/dividas}. Foco na
 * aritmética do parcelamento (a última parcela absorve o resíduo, podendo
 * ficar maior ou menor que as demais) e no status derivado.
 */
class DividaTest extends TesteIntegracaoBase {

    @Test
    void parcelamento_100por3_ultimaAbsorveResiduo_maiorQueAsDemais() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(100), 3, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());

        assertThat(parcelas).hasSize(3);
        assertThat(parcelas.get(0).getValor()).isEqualByComparingTo("33.33");
        assertThat(parcelas.get(1).getValor()).isEqualByComparingTo("33.33");
        assertThat(parcelas.get(2).getValor()).isEqualByComparingTo("33.34");
        BigDecimal soma = parcelas.stream().map(TransacaoDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("100");
    }

    @Test
    void parcelamento_100por6_ultimaMenorQueAsDemais() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(100), 6, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());

        assertThat(parcelas).hasSize(6);
        for (int i = 0; i < 5; i++) {
            assertThat(parcelas.get(i).getValor()).isEqualByComparingTo("16.67");
        }
        assertThat(parcelas.get(5).getValor()).isEqualByComparingTo("16.65");
        BigDecimal soma = parcelas.stream().map(TransacaoDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("100");
    }

    @Test
    void parcelamento_totalParcelas1_parcelaUnicaIgualAoTotal() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(250), 1, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());

        assertThat(parcelas).hasSize(1);
        assertThat(parcelas.get(0).getValor()).isEqualByComparingTo("250");
    }

    @Test
    void todasParcelasNascemPendentes_mesmoComDataInicioNoPassado_saldoNaoMudaNaCriacao() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now().minusMonths(5));

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000");
    }

    @Test
    void parcelas_vemOrdenadoPorNumeroParcela() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(300), 4, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());

        assertThat(parcelas).extracting(TransacaoDTO::getNumeroParcela).containsExactly(1, 2, 3, 4);
    }

    @Test
    void progresso_pagar2De3Parcelas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());

        pagar(token, parcelas.get(0).getId());
        pagar(token, parcelas.get(1).getId());

        DividaDTO atualizado = buscarDivida(token, divida.getId());
        assertThat(atualizado.getValorPago()).isEqualByComparingTo("60"); // 30 + 30
        assertThat(atualizado.getValorRestante()).isEqualByComparingTo("30");
        assertThat(atualizado.getParcelasPagas()).isEqualTo(2);
    }

    @Test
    void status_emDia_atrasada_vencimentoHojeContinuaEmDia_quitada_cancelada() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        // EM_DIA: próxima parcela vence no futuro
        DividaDTO emDia = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now().plusMonths(1));
        assertThat(buscarDivida(token, emDia.getId()).getStatus().name()).isEqualTo("EM_DIA");

        // vencimento == hoje continua EM_DIA (limite exato, não ATRASADA)
        DividaDTO venceHoje = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now());
        assertThat(buscarDivida(token, venceHoje.getId()).getStatus().name()).isEqualTo("EM_DIA");

        // ATRASADA: próxima parcela venceu antes de hoje
        DividaDTO atrasada = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now().minusMonths(2));
        assertThat(buscarDivida(token, atrasada.getId()).getStatus().name()).isEqualTo("ATRASADA");

        // QUITADA: todas as parcelas pagas
        DividaDTO quitavel = criarDivida(token, contaId, BigDecimal.valueOf(30), 1, LocalDate.now());
        List<TransacaoDTO> parcelaUnica = parcelas(token, quitavel.getId());
        pagar(token, parcelaUnica.get(0).getId());
        assertThat(buscarDivida(token, quitavel.getId()).getStatus().name()).isEqualTo("QUITADA");

        // CANCELADA
        DividaDTO cancelavel = criarDivida(token, contaId, BigDecimal.valueOf(30), 1, LocalDate.now());
        cancelar(token, cancelavel.getId());
        assertThat(buscarDivida(token, cancelavel.getId()).getStatus().name()).isEqualTo("CANCELADA");
    }

    @Test
    void cancelar_comParcelaPaga_bloqueadoCom400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());
        pagar(token, parcelas.get(0).getId());
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("970"); // 1000 - 30

        ResponseEntity<Map> resposta = patchComCorpoDeErro("/api/dividas/" + divida.getId() + "/cancelar", null, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Estorne todas as parcelas pagas antes de cancelar a dívida");
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("970"); // saldo intacto
    }

    @Test
    void cancelar_aposEstornoTotal_cancelaComSucesso() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());
        pagar(token, parcelas.get(0).getId());
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("970");

        estornar(token, parcelas.get(0).getId());
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000"); // saldo devolvido

        cancelar(token, divida.getId());
        assertThat(buscarDivida(token, divida.getId()).getStatus().name()).isEqualTo("CANCELADA");
    }

    @Test
    void delete_comParcelaPaga_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now());
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());
        pagar(token, parcelas.get(0).getId());

        ResponseEntity<Map> resposta = deleteComCorpo("/api/dividas/" + divida.getId(), token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Esta dívida já tem parcelas pagas — cancele em vez de excluir");
    }

    @Test
    void delete_semParcelaPaga_apagaDividaETodasTransacoesDoGrupo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(90), 3, LocalDate.now());

        ResponseEntity<Void> resposta = delete("/api/dividas/" + divida.getId(), token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> respostaParcelas = restTemplate.exchange(
                url("/api/dividas/" + divida.getId() + "/parcelas"), org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(autenticado(token)), Map.class);
        assertThat(respostaParcelas.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private DividaDTO criarDivida(String token, Long contaId, BigDecimal valorTotal, int totalParcelas, LocalDate dataInicio) {
        DividaDTO dto = new DividaDTO();
        dto.setDescricao("Dívida teste");
        dto.setCredor("Credor teste");
        dto.setValorTotal(valorTotal);
        dto.setTotalParcelas(totalParcelas);
        dto.setContaId(contaId);
        dto.setDataInicio(dataInicio);
        ResponseEntity<DividaDTO> resposta = post("/api/dividas", dto, token, DividaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private List<TransacaoDTO> parcelas(String token, Long dividaId) {
        ResponseEntity<List<TransacaoDTO>> resposta = get("/api/dividas/" + dividaId + "/parcelas", token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private DividaDTO buscarDivida(String token, Long dividaId) {
        ResponseEntity<List<DividaDTO>> resposta = get("/api/dividas", token, new ParameterizedTypeReference<List<DividaDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream().filter(d -> d.getId().equals(dividaId)).findFirst().orElseThrow();
    }

    private void pagar(String token, Long transacaoId) {
        ResponseEntity<Map> resposta = patch("/api/transacoes/" + transacaoId + "/pagar", null, token, Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void cancelar(String token, Long dividaId) {
        ResponseEntity<DividaDTO> resposta = patch("/api/dividas/" + dividaId + "/cancelar", null, token, DividaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void estornar(String token, Long transacaoId) {
        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + transacaoId + "/estornar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

}
