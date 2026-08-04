package com.financeiro;

import com.financeiro.dto.CartaoDTO;
import com.financeiro.dto.FaturaDTO;
import com.financeiro.dto.ItemFaturaDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Cobre {@code CartaoService}, {@code ItemFaturaService} e
 * {@code FaturaService} (telas Cartões e Detalhe do Cartão). Para exercitar
 * uma fatura fechada, injeta o {@code AgendadorFatura} e chama
 * {@code onDailyCheck()} diretamente com {@code diaFechamento} igual ao dia
 * de hoje — mesma técnica usada em AgendadorFaturaTest.
 */
class CartaoFaturaTest extends TesteIntegracaoBase {

    @Autowired
    private com.financeiro.scheduler.AgendadorFatura agendadorFatura;

    @Test
    void crud_cartao_contaPagamentoInexistente_404() {
        String token = registrar();
        CartaoDTO dto = cartao(999999L, 10, 20);

        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/cartoes", dto, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Conta de pagamento não encontrada");
    }

    @Test
    void crud_cartao_criaAtualizaApaga() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(token, contaId, 10, 20, BigDecimal.valueOf(500));

        CartaoDTO alteracao = cartao(contaId, 15, 25);
        alteracao.setLimite(BigDecimal.valueOf(800));
        alteracao.setNome("Cartão Atualizado");
        ResponseEntity<CartaoDTO> respostaUpdate = put("/api/cartoes/" + cartaoId, alteracao, token, CartaoDTO.class);
        assertThat(respostaUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaUpdate.getBody().getNome()).isEqualTo("Cartão Atualizado");
        assertThat(respostaUpdate.getBody().getLimite()).isEqualByComparingTo("800");

        ResponseEntity<Void> respostaDelete = delete("/api/cartoes/" + cartaoId, token);
        assertThat(respostaDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listarCartoes(token)).noneMatch(c -> c.getId().equals(cartaoId));
    }

    @Test
    void limiteDisponivel_itemAberto_reduz_itemCancelado_naoReduz() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(token, contaId, 28, 5, BigDecimal.valueOf(500));

        criarItem(token, cartaoId, BigDecimal.valueOf(100), LocalDate.now());
        assertThat(buscarCartao(token, cartaoId).getLimiteDisponivel()).isEqualByComparingTo("400");

        Long itemCanceladoId = criarItem(token, cartaoId, BigDecimal.valueOf(50), LocalDate.now()).get(0).getId();
        assertThat(buscarCartao(token, cartaoId).getLimiteDisponivel()).isEqualByComparingTo("350");

        ResponseEntity<ItemFaturaDTO> respostaCancelar = patch(
                "/api/itens-fatura/" + itemCanceladoId + "/cancelar", null, token, ItemFaturaDTO.class);
        assertThat(respostaCancelar.getStatusCode()).isEqualTo(HttpStatus.OK);

        // item cancelado não reduz mais o limite disponível
        assertThat(buscarCartao(token, cartaoId).getLimiteDisponivel()).isEqualByComparingTo("400");
    }

    @Test
    void limiteDisponivel_faturaFechadaNaoPaga_continuaReduzindo_pagarDevolveLimite() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        int diaFechamentoHoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(token, contaId, diaFechamentoHoje, 5, BigDecimal.valueOf(500));

        criarItem(token, cartaoId, BigDecimal.valueOf(200), LocalDate.now());
        agendadorFatura.onDailyCheck();

        // fatura fechada e não paga continua comprometendo o limite
        assertThat(buscarCartao(token, cartaoId).getLimiteDisponivel()).isEqualByComparingTo("300");
        // faturaAtualTotal não inclui faturas fechadas — item já migrou pra fatura
        assertThat(buscarCartao(token, cartaoId).getFaturaAtualTotal()).isEqualByComparingTo("0");

        FaturaDTO fatura = listarFaturas(token, cartaoId).get(0);
        pagarTransacao(token, fatura.getTransacaoDespesaId());

        assertThat(buscarCartao(token, cartaoId).getLimiteDisponivel()).isEqualByComparingTo("500");
    }

    @Test
    void limiteDisponivel_estouro_ficaNegativo_semClamp() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(token, contaId, 28, 5, BigDecimal.valueOf(100));

        criarItem(token, cartaoId, BigDecimal.valueOf(300), LocalDate.now());

        assertThat(buscarCartao(token, cartaoId).getLimiteDisponivel()).isEqualByComparingTo("-200");
    }

    @Test
    void parcelamento_gera3Itens_mesmoGrupo_valorTotalDivididoEntreParcelas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(token, contaId, 28, 5, BigDecimal.valueOf(5000));

        LocalDate dataBase = LocalDate.of(2024, 1, 15);
        ItemFaturaDTO dto = itemDto(cartaoId, BigDecimal.valueOf(100), dataBase);
        dto.setTotalParcelas(3);

        List<ItemFaturaDTO> criados = criarItens(token, dto);

        assertThat(criados).hasSize(3);
        String grupoId = criados.get(0).getGrupoParcelaId();
        assertThat(grupoId).isNotNull();
        // 100 dividido em 3 parcelas: 33.33 + 33.33 + 33.34 (a última absorve o resto do arredondamento)
        BigDecimal[] valoresEsperados = { BigDecimal.valueOf(33.33), BigDecimal.valueOf(33.33), BigDecimal.valueOf(33.34) };
        for (int i = 0; i < 3; i++) {
            ItemFaturaDTO item = criados.get(i);
            assertThat(item.getGrupoParcelaId()).isEqualTo(grupoId);
            assertThat(item.getNumeroParcela()).isEqualTo(i + 1);
            assertThat(item.getValor()).isEqualByComparingTo(valoresEsperados[i]);
            assertThat(item.getData()).isEqualTo(dataBase.plusMonths(i));
        }
        BigDecimal soma = criados.stream().map(ItemFaturaDTO::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("100");
    }

    @Test
    void totalParcelasNuloOuUm_geraItemUnico_semGrupo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(token, contaId, 28, 5, BigDecimal.valueOf(5000));

        List<ItemFaturaDTO> semParcelas = criarItem(token, cartaoId, BigDecimal.valueOf(50), LocalDate.now());
        assertThat(semParcelas).hasSize(1);
        assertThat(semParcelas.get(0).getGrupoParcelaId()).isNull();

        ItemFaturaDTO comUmaParcela = itemDto(cartaoId, BigDecimal.valueOf(50), LocalDate.now());
        comUmaParcela.setTotalParcelas(1);
        List<ItemFaturaDTO> resultado = criarItens(token, comUmaParcela);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getGrupoParcelaId()).isNull();
    }

    @Test
    void dataBase31_parcelaDeFevereiro_clampaParaUltimoDiaDoMes() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(token, contaId, 28, 5, BigDecimal.valueOf(5000));

        LocalDate dataBase = LocalDate.of(2024, 1, 31); // 2024 é bissexto: fevereiro tem 29 dias
        ItemFaturaDTO dto = itemDto(cartaoId, BigDecimal.valueOf(10), dataBase);
        dto.setTotalParcelas(2);

        List<ItemFaturaDTO> criados = criarItens(token, dto);

        assertThat(criados.get(1).getData()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void itemJaFaturado_update_delete_cancelar_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        int diaFechamentoHoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(token, contaId, diaFechamentoHoje, 5, BigDecimal.valueOf(500));

        Long itemId = criarItem(token, cartaoId, BigDecimal.valueOf(100), LocalDate.now()).get(0).getId();
        agendadorFatura.onDailyCheck();

        String mensagemEsperada = "Este item já faz parte de uma fatura fechada e não pode mais ser alterado";

        ItemFaturaDTO alteracao = itemDto(cartaoId, BigDecimal.valueOf(150), LocalDate.now());
        ResponseEntity<Map> respostaUpdate = restTemplate.exchange(
                url("/api/itens-fatura/" + itemId), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(alteracao, autenticado(token)), Map.class);
        assertThat(respostaUpdate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaUpdate.getBody().get("mensagem")).isEqualTo(mensagemEsperada);

        ResponseEntity<Map> respostaDelete = deleteComCorpo("/api/itens-fatura/" + itemId, token);
        assertThat(respostaDelete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaDelete.getBody().get("mensagem")).isEqualTo(mensagemEsperada);

        ResponseEntity<Map> respostaCancelar = patchComCorpoDeErro("/api/itens-fatura/" + itemId + "/cancelar", null, token);
        assertThat(respostaCancelar.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaCancelar.getBody().get("mensagem")).isEqualTo(mensagemEsperada);
    }

    @Test
    void getFatura_trazItensEStatus_pendenteParaPagaAposPagar() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        int diaFechamentoHoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(token, contaId, diaFechamentoHoje, 5, BigDecimal.valueOf(500));

        criarItem(token, cartaoId, BigDecimal.valueOf(100), LocalDate.now());
        agendadorFatura.onDailyCheck();

        FaturaDTO faturaResumo = listarFaturas(token, cartaoId).get(0);
        ResponseEntity<FaturaDTO> respostaDetalhe = get("/api/faturas/" + faturaResumo.getId(), token, FaturaDTO.class);
        assertThat(respostaDetalhe.getStatusCode()).isEqualTo(HttpStatus.OK);
        FaturaDTO detalhe = respostaDetalhe.getBody();
        assertThat(detalhe.getItens()).hasSize(1);
        assertThat(detalhe.getStatus().name()).isEqualTo("PENDENTE");

        pagarTransacao(token, detalhe.getTransacaoDespesaId());

        ResponseEntity<FaturaDTO> respostaAposPagar = get("/api/faturas/" + faturaResumo.getId(), token, FaturaDTO.class);
        assertThat(respostaAposPagar.getBody().getStatus().name()).isEqualTo("PAGA");
    }

    @Test
    void itensPorCiclo_separaOMesAtualDoMesSeguinte_porDataDeFechamento() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        LocalDate hoje = LocalDate.now();
        org.junit.jupiter.api.Assumptions.assumeTrue(hoje.getDayOfMonth() < hoje.lengthOfMonth() - 3,
                "precisa de folga no mês pra representar o cenário");
        int diaFechamento = hoje.getDayOfMonth() + 1;
        Long cartaoId = criarCartao(token, contaId, diaFechamento, Math.min(diaFechamento + 3, 28), BigDecimal.valueOf(5000));

        // Entra na fatura que fecha amanhã (ciclo do mês corrente).
        Long itemDoCiclo = criarItem(token, cartaoId, BigDecimal.valueOf(50), hoje).get(0).getId();
        // Parcela futura (ex.: 2ª parcela de uma compra parcelada) com data
        // depois do fechamento de amanhã — só entra no ciclo do mês seguinte.
        Long itemFuturo = criarItem(token, cartaoId, BigDecimal.valueOf(30), hoje.plusDays(3)).get(0).getId();

        YearMonth mesAtual = YearMonth.now();
        List<ItemFaturaDTO> cicloAtual = listarItensPorCiclo(token, cartaoId, mesAtual.toString());
        assertThat(cicloAtual).extracting(ItemFaturaDTO::getId).containsExactly(itemDoCiclo);

        List<ItemFaturaDTO> cicloSeguinte = listarItensPorCiclo(token, cartaoId, mesAtual.plusMonths(1).toString());
        assertThat(cicloSeguinte).extracting(ItemFaturaDTO::getId).containsExactly(itemFuturo);

        // Sem mês, traz os dois — todos os itens em aberto do cartão, sem filtro de ciclo.
        List<ItemFaturaDTO> semFiltro = listarItensPorCiclo(token, cartaoId, null);
        assertThat(semFiltro).extracting(ItemFaturaDTO::getId).containsExactlyInAnyOrder(itemDoCiclo, itemFuturo);
    }

    @Test
    void cartaoDeOutroEspaco_naoVaza_404() {
        String tokenA = registrar();
        Long contaA = criarConta(tokenA, BigDecimal.valueOf(1000));
        Long cartaoA = criarCartao(tokenA, contaA, 10, 20, BigDecimal.valueOf(500));

        String tokenB = registrar();
        ResponseEntity<Map> resposta = restTemplate.exchange(
                url("/api/cartoes/" + cartaoA), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(cartao(contaA, 10, 20), autenticado(tokenB)), Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void itemDeOutroEspaco_naoVaza_404() {
        String tokenA = registrar();
        Long contaA = criarConta(tokenA, BigDecimal.valueOf(1000));
        Long cartaoA = criarCartao(tokenA, contaA, 10, 20, BigDecimal.valueOf(500));
        Long itemA = criarItem(tokenA, cartaoA, BigDecimal.valueOf(50), LocalDate.now()).get(0).getId();

        String tokenB = registrar();
        ResponseEntity<Map> resposta = deleteComCorpo("/api/itens-fatura/" + itemA, tokenB);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private CartaoDTO cartao(Long contaPagamentoId, int diaFechamento, int diaVencimento) {
        CartaoDTO dto = new CartaoDTO();
        dto.setNome("Cartão Teste");
        dto.setLimite(BigDecimal.valueOf(500));
        dto.setDiaFechamento(diaFechamento);
        dto.setDiaVencimento(diaVencimento);
        dto.setContaPagamentoId(contaPagamentoId);
        dto.setCor("#000000");
        dto.setIcone("credit-card");
        return dto;
    }

    private Long criarCartao(String token, Long contaPagamentoId, int diaFechamento, int diaVencimento, BigDecimal limite) {
        CartaoDTO dto = cartao(contaPagamentoId, diaFechamento, diaVencimento);
        dto.setLimite(limite);
        ResponseEntity<CartaoDTO> resposta = post("/api/cartoes", dto, token, CartaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    private CartaoDTO buscarCartao(String token, Long cartaoId) {
        return listarCartoes(token).stream().filter(c -> c.getId().equals(cartaoId)).findFirst().orElseThrow();
    }

    private List<CartaoDTO> listarCartoes(String token) {
        ResponseEntity<List<CartaoDTO>> resposta = get("/api/cartoes", token, new ParameterizedTypeReference<List<CartaoDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private ItemFaturaDTO itemDto(Long cartaoId, BigDecimal valor, LocalDate data) {
        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setCartaoId(cartaoId);
        dto.setValor(valor);
        dto.setDescricao("item teste");
        dto.setData(data);
        return dto;
    }

    private List<ItemFaturaDTO> criarItem(String token, Long cartaoId, BigDecimal valor, LocalDate data) {
        return criarItens(token, itemDto(cartaoId, valor, data));
    }

    private List<ItemFaturaDTO> listarItensPorCiclo(String token, Long cartaoId, String month) {
        String caminho = "/api/itens-fatura/ciclo?cartaoId=" + cartaoId + (month != null ? "&month=" + month : "");
        ResponseEntity<List<ItemFaturaDTO>> resposta = get(caminho, token,
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private List<ItemFaturaDTO> criarItens(String token, ItemFaturaDTO dto) {
        ResponseEntity<List<ItemFaturaDTO>> resposta = post("/api/itens-fatura", dto, token,
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private List<FaturaDTO> listarFaturas(String token, Long cartaoId) {
        ResponseEntity<List<FaturaDTO>> resposta = get("/api/faturas?cartaoId=" + cartaoId, token,
                new ParameterizedTypeReference<List<FaturaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotEmpty();
        return resposta.getBody();
    }

    private void pagarTransacao(String token, Long transacaoId) {
        ResponseEntity<Map> resposta = patch("/api/transacoes/" + transacaoId + "/pagar", null, token, Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
