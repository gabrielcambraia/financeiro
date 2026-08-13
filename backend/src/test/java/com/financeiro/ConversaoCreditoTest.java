package com.financeiro;

import com.financeiro.dto.FaturaDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.EscopoAtualizacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
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
 * Testes de integração para {@code POST /api/transacoes/{id}/converter-para-cartao}
 * (débito → crédito). Valida os cenários principais: conversão em período aberto,
 * fatura fechada pendente, fatura já paga, transação cancelada, parcelas e séries
 * fixas com escopo UNICA e FUTURAS.
 */
class ConversaoCreditoTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorFatura agendadorFatura;

    @Autowired
    private ItemFaturaRepository itemFaturaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    // -----------------------------------------------------------------------
    // 1. Débito em período aberto → converte para crédito, saldo revertido
    // -----------------------------------------------------------------------

    @Test
    void debito_periodoAberto_converteParaCredito_saldoRevertido() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        LocalDate hoje = LocalDate.now();

        Long txId = criarTransacao(u.token(), u.contaId(), BigDecimal.valueOf(200), hoje);

        // saldo deve ter sido debitado imediatamente (saldoAjustado=true)
        BigDecimal saldoAposTransacao = saldoConta(u.token(), u.contaId());
        assertThat(saldoAposTransacao).isEqualByComparingTo("800");

        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);

        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/transacoes/" + txId + "/converter-para-cartao",
                corpo, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).hasSize(1);

        // item ainda não está vinculado a nenhuma fatura (período aberto)
        assertThat(resposta.getBody().get(0).getFaturaId()).isNull();

        // saldo deve ter voltado ao valor inicial (saldo revertido)
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("1000");

        // transação original deve ter sido excluída
        Long espacoId = u.espacoId();
        assertThat(transacaoRepository.findByIdAndEspacoId(txId, espacoId)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 2. Fatura fechada pendente → item criado anexado à fatura; valor incrementado
    // -----------------------------------------------------------------------

    @Test
    void debito_faturaFechadaPendente_converteEAtualiza_valorFatura() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        int diaFechamento = LocalDate.now().getDayOfMonth();
        int diaVencimento = Math.min(diaFechamento + 5, 28);
        Long cartaoId = criarCartao(u.token(), u.contaId(), diaFechamento, diaVencimento);

        // cria item no cartão para fechar a fatura com algum valor
        Long itemExistenteId = criarItem(u.token(), cartaoId, BigDecimal.valueOf(100), LocalDate.now());

        // fecha a fatura
        agendadorFatura.onDailyCheck();

        FaturaDTO faturaFechada = listarFaturas(u.token(), cartaoId).get(0);
        BigDecimal valorFaturaAntes = faturaFechada.getValor();

        // cria transacao débito e converte para o mesmo cartão (fatura já fechada)
        Long txId = criarTransacao(u.token(), u.contaId(), BigDecimal.valueOf(150), LocalDate.now());

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);

        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/transacoes/" + txId + "/converter-para-cartao",
                corpo, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ItemFaturaDTO itemCriado = resposta.getBody().get(0);

        // o item deve estar vinculado à fatura fechada
        assertThat(itemCriado.getFaturaId()).isNotNull();

        // valor da fatura deve ter aumentado
        FaturaDTO faturaAtualizada = listarFaturas(u.token(), cartaoId).get(0);
        assertThat(faturaAtualizada.getValor()).isGreaterThan(valorFaturaAntes);
    }

    // -----------------------------------------------------------------------
    // 3. Fatura alvo já paga → bloqueado com 400
    // -----------------------------------------------------------------------

    @Test
    void debito_faturaAlvoPaga_bloqueia_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        int diaFechamento = LocalDate.now().getDayOfMonth();
        int diaVencimento = Math.min(diaFechamento + 5, 28);
        Long cartaoId = criarCartao(u.token(), u.contaId(), diaFechamento, diaVencimento);

        criarItem(u.token(), cartaoId, BigDecimal.valueOf(50), LocalDate.now());
        agendadorFatura.onDailyCheck();

        // paga a fatura
        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        pagarTransacao(u.token(), fatura.getTransacaoDespesaId());

        // tenta converter transação débito para o cartão com fatura paga
        Long txId = criarTransacao(u.token(), u.contaId(), BigDecimal.valueOf(100), LocalDate.now());

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro(
                "/api/transacoes/" + txId + "/converter-para-cartao",
                corpo, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaErro.getBody().get("mensagem").toString()).contains("já paga");
    }

    // -----------------------------------------------------------------------
    // 4. Transação cancelada → bloqueado com 400
    // -----------------------------------------------------------------------

    @Test
    void debito_cancelado_bloqueia_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        Long txId = criarTransacao(u.token(), u.contaId(), BigDecimal.valueOf(100), LocalDate.now());

        // cancela a transação
        patch("/api/transacoes/" + txId + "/cancelar?scope=UNICA", null, u.token(), TransacaoDTO.class);

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro(
                "/api/transacoes/" + txId + "/converter-para-cartao",
                corpo, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaErro.getBody().get("mensagem").toString()).contains("cancelada");
    }

    // -----------------------------------------------------------------------
    // 5. Parcela isolada → bloqueado com 400
    // -----------------------------------------------------------------------

    @Test
    void debito_parcela_bloqueia_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        // cria transação parcelada (3 parcelas) — devolve lista de 3 itens
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(300));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setTotalParcelas(3);
        dto.setQuitarNaCriacao(true);

        ResponseEntity<List<TransacaoDTO>> respostaCriacao = post(
                "/api/transacoes", dto, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});
        assertThat(respostaCriacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long primeiraParcelaId = respostaCriacao.getBody().get(0).getId();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro(
                "/api/transacoes/" + primeiraParcelaId + "/converter-para-cartao",
                corpo, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaErro.getBody().get("mensagem").toString()).contains("parcela");
    }

    // -----------------------------------------------------------------------
    // 6. Receita (não despesa) → bloqueado com 400
    // -----------------------------------------------------------------------

    @Test
    void debito_naoDeSpesa_bloqueia_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        // cria transação de RECEITA
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.RECEITA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(100));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setQuitarNaCriacao(true);

        ResponseEntity<List<TransacaoDTO>> respostaCriacao = post(
                "/api/transacoes", dto, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});
        assertThat(respostaCriacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long txId = respostaCriacao.getBody().get(0).getId();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro(
                "/api/transacoes/" + txId + "/converter-para-cartao",
                corpo, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaErro.getBody().get("mensagem").toString()).contains("débito");
    }

    // -----------------------------------------------------------------------
    // 7. Fixa com escopo UNICA → converte apenas 1 item; demais transações fixas permanecem
    // -----------------------------------------------------------------------

    @Test
    void debito_fixa_unica_converteApenas1() {
        Usuario u = registrarComConta(BigDecimal.valueOf(5000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        // cria transação fixa — a API cria a do mês atual + 11 futuras
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(100));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setFixa(true);
        dto.setQuitarNaCriacao(true);

        ResponseEntity<List<TransacaoDTO>> respostaCriacao = post(
                "/api/transacoes", dto, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});
        assertThat(respostaCriacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        long totalAntes = transacaoRepository.findAll().stream()
                .filter(t -> u.espacoId().equals(t.getEspacoId())).count();

        // id da transação da cabeça (mês atual)
        Long txId = respostaCriacao.getBody().get(0).getId();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);
        corpo.put("escopo", EscopoAtualizacao.UNICA.name());

        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/transacoes/" + txId + "/converter-para-cartao",
                corpo, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).hasSize(1);

        // as demais transações fixas (futuras) ainda devem existir
        long totalDepois = transacaoRepository.findAll().stream()
                .filter(t -> u.espacoId().equals(t.getEspacoId())).count();
        assertThat(totalDepois).isEqualTo(totalAntes - 1);
    }

    // -----------------------------------------------------------------------
    // 8. Fixa com escopo FUTURAS → converte todas; saldo revertido apenas uma vez
    // -----------------------------------------------------------------------

    @Test
    void debito_fixa_futuras_converteTodas_saldoRevertido() {
        Usuario u = registrarComConta(BigDecimal.valueOf(5000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(100));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setFixa(true);
        dto.setQuitarNaCriacao(true);

        ResponseEntity<List<TransacaoDTO>> respostaCriacao = post(
                "/api/transacoes", dto, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});
        assertThat(respostaCriacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // apenas a cabeça tem saldoAjustado=true, logo saldo foi debitado 1x
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("4900");

        Long txId = respostaCriacao.getBody().get(0).getId();

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);
        corpo.put("escopo", EscopoAtualizacao.FUTURAS.name());

        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/transacoes/" + txId + "/converter-para-cartao",
                corpo, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // deve ter criado múltiplos itens (pelo menos 12)
        assertThat(resposta.getBody().size()).isGreaterThanOrEqualTo(12);

        // todas as transações fixas do espaço devem ter sido excluídas
        long txFixasRestantes = transacaoRepository.findAll().stream()
                .filter(t -> u.espacoId().equals(t.getEspacoId()) && t.isFixa())
                .count();
        assertThat(txFixasRestantes).isEqualTo(0);

        // saldo deve ter voltado ao inicial (apenas 1 reversão, só a cabeça era ajustada)
        assertThat(saldoConta(u.token(), u.contaId())).isEqualByComparingTo("5000");
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
