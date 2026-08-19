package com.financeiro;

import com.financeiro.dto.FaturaDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.EscopoAtualizacao;
import com.financeiro.repository.FaturaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorFatura;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para {@code POST /api/itens-fatura/{id}/converter-para-debito}
 * (crédito → débito). Valida conversão em período aberto com e sem quitação,
 * fatura fechada pendente, fatura paga, item cancelado, parcelas e séries fixas.
 */
class ConversaoDebitoTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorFatura agendadorFatura;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private FaturaRepository faturaRepository;

    @Autowired
    private ItemFaturaRepository itemFaturaRepository;

    // -----------------------------------------------------------------------
    // 1. Item em período aberto, sem quitação → transação PENDENTE, saldo inalterado
    // -----------------------------------------------------------------------

    @Test
    void credito_periodoAberto_converteParaDebito_semQuitacao() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        Long itemId = criarItem(u.token(), cartaoId, BigDecimal.valueOf(200), LocalDate.now());

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", false);

        ResponseEntity<List<TransacaoDTO>> resposta = post(
                "/api/itens-fatura/" + itemId + "/converter-para-debito",
                corpo, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).hasSize(1);

        TransacaoDTO tx = resposta.getBody().get(0);
        // transação deve ser PENDENTE (saldoAjustado=false → status PENDENTE)
        assertThat(tx.getStatus().name()).isEqualTo("PENDENTE");

        // saldo não deve ter mudado (sem quitação)
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("1000");

        // item deve ter sido excluído
        assertThat(itemFaturaRepository.findById(itemId)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 2. Item em período aberto, com quitação → saldo debitado
    // -----------------------------------------------------------------------

    @Test
    void credito_periodoAberto_converteComQuitacao_saldoDebitado() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        Long itemId = criarItem(u.token(), cartaoId, BigDecimal.valueOf(300), LocalDate.now());

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", true);

        ResponseEntity<List<TransacaoDTO>> resposta = post(
                "/api/itens-fatura/" + itemId + "/converter-para-debito",
                corpo, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).hasSize(1);

        TransacaoDTO tx = resposta.getBody().get(0);
        // transação deve estar paga
        assertThat(tx.getStatus().name()).isEqualTo("PAGA");

        // saldo deve ter sido debitado
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("700");

        // item original excluído
        assertThat(itemFaturaRepository.findById(itemId)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 3. Fatura fechada pendente → desanexa item, fatura tem valor decrementado
    // -----------------------------------------------------------------------

    @Test
    void credito_faturaFechadaPendente_desanexaEAtualizaFatura() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        int diaFechamento = LocalDate.now().getDayOfMonth();
        int diaVencimento = Math.min(diaFechamento + 5, 28);
        Long cartaoId = criarCartao(u.token(), u.contaId(), diaFechamento, diaVencimento);

        Long itemId = criarItem(u.token(), cartaoId, BigDecimal.valueOf(250), LocalDate.now());

        // fecha a fatura
        agendadorFatura.onDailyCheck();

        FaturaDTO faturaFechada = listarFaturas(u.token(), cartaoId).get(0);
        BigDecimal valorFaturaAntes = faturaFechada.getValor();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", false);

        ResponseEntity<List<TransacaoDTO>> resposta = post(
                "/api/itens-fatura/" + itemId + "/converter-para-debito",
                corpo, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).hasSize(1);

        // fatura deve ter valor decrementado
        FaturaDTO faturaAtualizada = listarFaturas(u.token(), cartaoId).get(0);
        assertThat(faturaAtualizada.getValor()).isLessThan(valorFaturaAntes);

        // item deve ter sido excluído
        assertThat(itemFaturaRepository.findById(itemId)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 4. Fatura já paga → bloqueado com 400
    // -----------------------------------------------------------------------

    @Test
    void credito_faturaAlvoPaga_bloqueia_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        int diaFechamento = LocalDate.now().getDayOfMonth();
        int diaVencimento = Math.min(diaFechamento + 5, 28);
        Long cartaoId = criarCartao(u.token(), u.contaId(), diaFechamento, diaVencimento);

        Long itemId = criarItem(u.token(), cartaoId, BigDecimal.valueOf(100), LocalDate.now());

        agendadorFatura.onDailyCheck();

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        pagarTransacao(u.token(), fatura.getTransacaoDespesaId());

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", false);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro(
                "/api/itens-fatura/" + itemId + "/converter-para-debito",
                corpo, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaErro.getBody().get("mensagem").toString()).contains("paga");
    }

    // -----------------------------------------------------------------------
    // 5. Item cancelado → bloqueado com 400
    // -----------------------------------------------------------------------

    @Test
    void credito_cancelado_bloqueia_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        Long itemId = criarItem(u.token(), cartaoId, BigDecimal.valueOf(80), LocalDate.now());

        // cancela o item
        patch("/api/itens-fatura/" + itemId + "/cancelar", null, u.token(), ItemFaturaDTO.class);

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", false);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro(
                "/api/itens-fatura/" + itemId + "/converter-para-debito",
                corpo, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaErro.getBody().get("mensagem").toString()).contains("cancelado");
    }

    // -----------------------------------------------------------------------
    // 6. Parcela isolada de crédito → bloqueado com 400
    // -----------------------------------------------------------------------

    @Test
    void credito_parcela_bloqueia_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        // cria item parcelado (3 parcelas)
        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setCartaoId(cartaoId);
        dto.setValor(BigDecimal.valueOf(300));
        dto.setDescricao("compra parcelada");
        dto.setData(LocalDate.now());
        dto.setTotalParcelas(3);

        ResponseEntity<List<ItemFaturaDTO>> respostaCriacao = post(
                "/api/itens-fatura", dto, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});
        assertThat(respostaCriacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long primeiraParcelaId = respostaCriacao.getBody().get(0).getId();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", false);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro(
                "/api/itens-fatura/" + primeiraParcelaId + "/converter-para-debito",
                corpo, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaErro.getBody().get("mensagem").toString()).contains("parcela");
    }

    // -----------------------------------------------------------------------
    // 7. Item fixa, escopo UNICA → converte apenas 1; restantes intactos
    // -----------------------------------------------------------------------

    @Test
    void credito_fixa_unica_converteApenas1() {
        Usuario u = registrarComConta(BigDecimal.valueOf(3000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        // cria série fixa via Map (campo "fixa" não está na validação mas existe no ItemFaturaDTO)
        Map<String, Object> dtoFixo = new HashMap<>();
        dtoFixo.put("cartaoId", cartaoId);
        dtoFixo.put("valor", 120);
        dtoFixo.put("descricao", "item fixa");
        dtoFixo.put("data", LocalDate.now().toString());
        dtoFixo.put("fixa", true);

        ResponseEntity<List<ItemFaturaDTO>> respostaCriacao = post(
                "/api/itens-fatura", dtoFixo, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});
        assertThat(respostaCriacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respostaCriacao.getBody()).hasSize(1);

        Long cabecaId = respostaCriacao.getBody().get(0).getId();
        long totalItensAntes = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId())).count();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", false);
        corpo.put("escopo", EscopoAtualizacao.UNICA.name());

        ResponseEntity<List<TransacaoDTO>> resposta = post(
                "/api/itens-fatura/" + cabecaId + "/converter-para-debito",
                corpo, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).hasSize(1);

        // 0 itens restantes (foi o único criado; UNICA remove apenas este)
        long totalItensDepois = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId())).count();
        assertThat(totalItensDepois).isEqualTo(totalItensAntes - 1);
    }

    // -----------------------------------------------------------------------
    // 8. Item fixa, escopo FUTURAS → converte todos; itens originais excluídos
    // -----------------------------------------------------------------------

    @Test
    void credito_fixa_futuras_converteTodas() {
        Usuario u = registrarComConta(BigDecimal.valueOf(3000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        Map<String, Object> dtoFixo = new HashMap<>();
        dtoFixo.put("cartaoId", cartaoId);
        dtoFixo.put("valor", 90);
        dtoFixo.put("descricao", "item fixa futuras");
        dtoFixo.put("data", LocalDate.now().toString());
        dtoFixo.put("fixa", true);

        ResponseEntity<List<ItemFaturaDTO>> respostaCriacao = post(
                "/api/itens-fatura", dtoFixo, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});
        assertThat(respostaCriacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respostaCriacao.getBody()).hasSize(1);

        Long cabecaId = respostaCriacao.getBody().get(0).getId();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        corpo.put("quitarNaCriacao", false);
        corpo.put("escopo", EscopoAtualizacao.FUTURAS.name());

        ResponseEntity<List<TransacaoDTO>> resposta = post(
                "/api/itens-fatura/" + cabecaId + "/converter-para-debito",
                corpo, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().size()).isEqualTo(1);

        // todos os itens fixos do espaço devem ter sido excluídos
        long itensFixosRestantes = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId()) && i.isFixa())
                .count();
        assertThat(itensFixosRestantes).isEqualTo(0);
    }

    private Long criarItem(String token, Long cartaoId, BigDecimal valor, LocalDate data) {
        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setCartaoId(cartaoId);
        dto.setValor(valor);
        dto.setDescricao("item");
        dto.setData(data);
        return post("/api/itens-fatura", dto, token,
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {})
                .getBody().get(0).getId();
    }
}
