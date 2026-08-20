package com.financeiro;

import com.financeiro.dto.CartaoDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.FaturaDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.entity.enums.TipoConta;
import com.financeiro.entity.enums.TipoPessoa;
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
import java.util.UUID;

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

    @Test
    void itensFatura_defaultOmiteFaturados_incluirFaturadosTrazAmbos() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        int diaFechamentoHoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(token, contaId, diaFechamentoHoje, 5, BigDecimal.valueOf(500));

        Long itemFaturadoId = criarItem(token, cartaoId, BigDecimal.valueOf(100), LocalDate.now()).get(0).getId();
        agendadorFatura.onDailyCheck();
        // Criado depois do fechamento de hoje: POST sempre nasce em aberto,
        // mesmo que a data caia num ciclo já fechado (ver ItemFaturaService.create).
        Long itemAbertoId = criarItem(token, cartaoId, BigDecimal.valueOf(30), LocalDate.now()).get(0).getId();

        String mes = YearMonth.now().toString();
        assertThat(listarItensFatura(token, cartaoId, mes, null))
                .extracting(ItemFaturaDTO::getId).containsExactly(itemAbertoId);
        assertThat(listarItensFatura(token, cartaoId, mes, false))
                .extracting(ItemFaturaDTO::getId).containsExactly(itemAbertoId);
        assertThat(listarItensFatura(token, cartaoId, mes, true))
                .extracting(ItemFaturaDTO::getId).containsExactlyInAnyOrder(itemFaturadoId, itemAbertoId);
    }

    @Test
    void faturaDataFechamento_itemFaturado_igualAoFechamentoDaFatura() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        int diaFechamentoHoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(token, contaId, diaFechamentoHoje, 5, BigDecimal.valueOf(500));

        Long itemId = criarItem(token, cartaoId, BigDecimal.valueOf(100), LocalDate.now()).get(0).getId();
        agendadorFatura.onDailyCheck();

        FaturaDTO fatura = listarFaturas(token, cartaoId).get(0);
        ItemFaturaDTO item = listarItensFatura(token, cartaoId, YearMonth.now().toString(), true).stream()
                .filter(i -> i.getId().equals(itemId)).findFirst().orElseThrow();

        assertThat(item.getFaturaDataFechamento()).isEqualTo(fatura.getDataFechamento());
    }

    @Test
    void faturaDataFechamento_itemAberto_ehProjecaoDoProximoFechamento() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        LocalDate hoje = LocalDate.now();
        org.junit.jupiter.api.Assumptions.assumeTrue(hoje.getDayOfMonth() < hoje.lengthOfMonth() - 3,
                "precisa de folga no mês pra representar o cenário");
        int diaFechamento = hoje.getDayOfMonth() + 1;
        Long cartaoId = criarCartao(token, contaId, diaFechamento, Math.min(diaFechamento + 3, 28), BigDecimal.valueOf(5000));

        // Ainda dentro do ciclo que fecha amanhã.
        Long itemCicloAtualId = criarItem(token, cartaoId, BigDecimal.valueOf(50), hoje).get(0).getId();
        // Depois do fechamento de amanhã: projeta pro fechamento do mês seguinte.
        Long itemCicloSeguinteId = criarItem(token, cartaoId, BigDecimal.valueOf(30), hoje.plusDays(3)).get(0).getId();

        YearMonth mesAtual = YearMonth.now();
        LocalDate fechamentoAtual = mesAtual.atDay(Math.min(diaFechamento, mesAtual.lengthOfMonth()));
        YearMonth mesSeguinte = mesAtual.plusMonths(1);
        LocalDate fechamentoSeguinte = mesSeguinte.atDay(Math.min(diaFechamento, mesSeguinte.lengthOfMonth()));

        List<ItemFaturaDTO> itens = listarItensFatura(token, cartaoId, mesAtual.toString(), true);
        ItemFaturaDTO itemCicloAtual = itens.stream().filter(i -> i.getId().equals(itemCicloAtualId)).findFirst().orElseThrow();
        ItemFaturaDTO itemCicloSeguinte = itens.stream().filter(i -> i.getId().equals(itemCicloSeguinteId)).findFirst().orElseThrow();

        assertThat(itemCicloAtual.getFaturaDataFechamento()).isEqualTo(fechamentoAtual);
        assertThat(itemCicloSeguinte.getFaturaDataFechamento()).isEqualTo(fechamentoSeguinte);
    }

    @Test
    void itensFatura_filtraPorEntidade_globalApareceEmTodosOsFiltros() {
        RespostaAutenticacao auth = registrarCompleto("Teste Entidade Cartão", "cartao-entidade" + UUID.randomUUID() + "@teste.com");
        String token = auth.getToken();
        ativarPlanoEmpresa(auth.getEspacoId());

        ResponseEntity<List<Map>> entidades = get("/api/entidades", token, new ParameterizedTypeReference<List<Map>>() {});
        Long entidadePfId = ((Number) entidades.getBody().get(0).get("id")).longValue();
        Long entidadePjId = criarEntidade(token, "PJ Cartão Teste", TipoPessoa.JURIDICA);

        Long contaPfId = criarContaComEntidade(token, entidadePfId);
        Long contaPjId = criarContaComEntidade(token, entidadePjId);
        Long contaGlobalId = criarContaComEntidade(token, null);

        Long cartaoPfId = criarCartao(token, contaPfId, 10, 20);
        Long cartaoPjId = criarCartao(token, contaPjId, 10, 20);
        Long cartaoGlobalId = criarCartao(token, contaGlobalId, 10, 20);

        Long itemPfId = criarItem(token, cartaoPfId, BigDecimal.valueOf(10), LocalDate.now()).get(0).getId();
        Long itemPjId = criarItem(token, cartaoPjId, BigDecimal.valueOf(10), LocalDate.now()).get(0).getId();
        Long itemGlobalId = criarItem(token, cartaoGlobalId, BigDecimal.valueOf(10), LocalDate.now()).get(0).getId();

        assertThat(extrairIds(listarItensFaturaComEntidade(token, null)))
                .contains(itemPfId, itemPjId, itemGlobalId);

        List<Long> filtroPf = extrairIds(listarItensFaturaComEntidade(token, entidadePfId));
        assertThat(filtroPf).contains(itemPfId, itemGlobalId);
        assertThat(filtroPf).doesNotContain(itemPjId);

        List<Long> filtroPj = extrairIds(listarItensFaturaComEntidade(token, entidadePjId));
        assertThat(filtroPj).contains(itemPjId, itemGlobalId);
        assertThat(filtroPj).doesNotContain(itemPfId);
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

    // incluirFaturados nulo = parâmetro omitido da URL, pra testar o default do controller.
    private List<ItemFaturaDTO> listarItensFatura(String token, Long cartaoId, String month, Boolean incluirFaturados) {
        StringBuilder caminho = new StringBuilder("/api/itens-fatura?cartaoId=" + cartaoId);
        if (month != null) caminho.append("&month=").append(month);
        if (incluirFaturados != null) caminho.append("&incluirFaturados=").append(incluirFaturados);
        ResponseEntity<List<ItemFaturaDTO>> resposta = get(caminho.toString(), token,
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private List<ItemFaturaDTO> listarItensFaturaComEntidade(String token, Long entidadeId) {
        ResponseEntity<List<ItemFaturaDTO>> resposta = entidadeId != null
                ? get("/api/itens-fatura", token, entidadeId, new ParameterizedTypeReference<List<ItemFaturaDTO>>() {})
                : get("/api/itens-fatura", token, new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private Long criarContaComEntidade(String token, Long entidadeId) {
        ContaDTO dto = new ContaDTO();
        dto.setNome("Conta Teste");
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldoInicial(BigDecimal.valueOf(1000));
        dto.setCor("#6366f1");
        dto.setIcone("wallet");
        dto.setEntidadeId(entidadeId);
        ResponseEntity<ContaDTO> resposta = post("/api/contas", dto, token, ContaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    private List<Long> extrairIds(List<ItemFaturaDTO> itens) {
        return itens.stream().map(ItemFaturaDTO::getId).toList();
    }

}
