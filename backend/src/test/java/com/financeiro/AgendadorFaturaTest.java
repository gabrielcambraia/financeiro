package com.financeiro;

import com.financeiro.dto.FaturaDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.entity.ItemFatura;
import com.financeiro.entity.Transacao;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.FaturaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorFatura;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link AgendadorFatura}: chama {@code onDailyCheck()} diretamente
 * (o {@code process()} interno é privado). Como as regras dependem de
 * {@code LocalDate.now()}, os cenários são controlados pelo
 * {@code diaFechamento}/{@code diaVencimento} do cartão em relação a hoje,
 * nunca mockando o relógio. Como {@code process()} varre
 * {@code cartaoRepository.findAll()} sem filtro de espaço, toda asserção
 * agregada é filtrada pelo {@code espacoId} do usuário criado no teste.
 */
class AgendadorFaturaTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorFatura agendador;

    @Autowired
    private CartaoRepository cartaoRepository;

    @Autowired
    private ItemFaturaRepository itemFaturaRepository;

    @Autowired
    private FaturaRepository faturaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Test
    void diaFechamentoIgualHoje_fechaFatura_geraDespesaPendente_saldoIntacto() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, proximoDiaValido(hoje));

        criarItem(u.token(), cartaoId, BigDecimal.valueOf(150));

        agendador.onDailyCheck();

        List<FaturaDTO> faturas = listarFaturas(u.token(), cartaoId);
        assertThat(faturas).hasSize(1);
        FaturaDTO fatura = faturas.get(0);
        assertThat(fatura.getDataFechamento()).isEqualTo(LocalDate.now());
        assertThat(fatura.getStatus().name()).isEqualTo("PENDENTE");

        Transacao despesa = transacaoRepository.findByIdAndEspacoId(fatura.getTransacaoDespesaId(), u.espacoId()).orElseThrow();
        assertThat(despesa.isSaldoAjustado()).isFalse();
        assertThat(despesa.getValor()).isEqualByComparingTo("150");
        assertThat(contaRepository.findById(u.contaId()).orElseThrow().getSaldo()).isEqualByComparingTo("1000");
    }

    @Test
    void diaFechamentoMaiorQueHoje_naoFecha() {
        LocalDate hoje = LocalDate.now();
        Assumptions.assumeTrue(hoje.getDayOfMonth() < hoje.lengthOfMonth(),
                "irrepresentável no último dia do mês");

        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int diaFechamento = hoje.getDayOfMonth() + 1;
        Long cartaoId = criarCartao(u.token(), u.contaId(), diaFechamento, proximoDiaValido(diaFechamento));

        criarItem(u.token(), cartaoId, BigDecimal.valueOf(150));

        agendador.onDailyCheck();

        assertThat(listarFaturas(u.token(), cartaoId)).isEmpty();
    }

    @Test
    void descricaoExata_faturaCartaoMesAno() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, proximoDiaValido(hoje));

        criarItem(u.token(), cartaoId, BigDecimal.valueOf(50));
        agendador.onDailyCheck();

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        Transacao despesa = transacaoRepository.findByIdAndEspacoId(fatura.getTransacaoDespesaId(), u.espacoId()).orElseThrow();
        String esperado = "Fatura Cartão Teste " + String.format("%02d/%d",
                fatura.getDataVencimento().getMonthValue(), fatura.getDataVencimento().getYear());
        assertThat(despesa.getDescricao()).isEqualTo(esperado);
    }

    @Test
    void itensAbertos_apontamParaFatura_itensCanceladosFicamDeFora() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, proximoDiaValido(hoje));

        Long itemAberto = criarItem(u.token(), cartaoId, BigDecimal.valueOf(100)).getId();
        Long itemCancelado = criarItem(u.token(), cartaoId, BigDecimal.valueOf(999)).getId();
        patch("/api/itens-fatura/" + itemCancelado + "/cancelar", null, u.token(), ItemFaturaDTO.class);

        agendador.onDailyCheck();

        ItemFatura aberto = itemFaturaRepository.findByIdAndEspacoId(itemAberto, u.espacoId()).orElseThrow();
        ItemFatura cancelado = itemFaturaRepository.findByIdAndEspacoId(itemCancelado, u.espacoId()).orElseThrow();
        assertThat(aberto.getFatura()).isNotNull();
        assertThat(cancelado.getFatura()).isNull();

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        assertThat(fatura.getValor()).isEqualByComparingTo("100");
    }

    @Test
    void itemComDataFutura_naoEntraNaFaturaDeHoje_ficaAbertoParaProximoFechamento() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, proximoDiaValido(hoje));

        Long itemDeHoje = criarItem(u.token(), cartaoId, BigDecimal.valueOf(100)).getId();
        // Parcela de uma compra parcelada que cai no próximo ciclo — não pode
        // ser varrida para a fatura que fecha hoje.
        Long itemFuturo = criarItemComData(u.token(), cartaoId, BigDecimal.valueOf(60), LocalDate.now().plusDays(1)).getId();

        agendador.onDailyCheck();

        ItemFatura deHoje = itemFaturaRepository.findByIdAndEspacoId(itemDeHoje, u.espacoId()).orElseThrow();
        ItemFatura futuro = itemFaturaRepository.findByIdAndEspacoId(itemFuturo, u.espacoId()).orElseThrow();
        assertThat(deHoje.getFatura()).isNotNull();
        assertThat(futuro.getFatura()).isNull();

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        assertThat(fatura.getValor()).isEqualByComparingTo("100");
    }

    @Test
    void idempotencia_naoDuplicaFaturaNoMesmoDia() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, proximoDiaValido(hoje));
        criarItem(u.token(), cartaoId, BigDecimal.valueOf(80));

        agendador.onDailyCheck();
        agendador.onDailyCheck();

        assertThat(listarFaturas(u.token(), cartaoId)).hasSize(1);
    }

    @Test
    void semItensAbertos_nenhumaFaturaOuTransacaoCriada() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, proximoDiaValido(hoje));

        agendador.onDailyCheck();

        assertThat(listarFaturas(u.token(), cartaoId)).isEmpty();
    }

    @Test
    void vencimentoAntesDoFechamento_caiNoMesSeguinte() {
        LocalDate hoje = LocalDate.now();
        Assumptions.assumeTrue(hoje.getDayOfMonth() > 1, "irrepresentável no dia 1 do mês");

        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int diaFechamento = hoje.getDayOfMonth();
        int diaVencimento = diaFechamento - 1; // menor que o fechamento -> mês seguinte
        Long cartaoId = criarCartao(u.token(), u.contaId(), diaFechamento, diaVencimento);

        criarItem(u.token(), cartaoId, BigDecimal.valueOf(50));
        agendador.onDailyCheck();

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        YearMonth mesFechamento = YearMonth.from(hoje);
        assertThat(YearMonth.from(fatura.getDataVencimento())).isEqualTo(mesFechamento.plusMonths(1));
        assertThat(fatura.getDataVencimento().getDayOfMonth()).isEqualTo(diaVencimento);
    }

    @Test
    void vencimentoDepoisDoFechamento_mesmoMes() {
        LocalDate hoje = LocalDate.now();
        Assumptions.assumeTrue(hoje.getDayOfMonth() < 27, "precisa de folga pra caber diaVencimento maior no mesmo mês");

        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int diaFechamento = hoje.getDayOfMonth();
        int diaVencimento = Math.min(28, diaFechamento + 1);
        Long cartaoId = criarCartao(u.token(), u.contaId(), diaFechamento, diaVencimento);

        criarItem(u.token(), cartaoId, BigDecimal.valueOf(50));
        agendador.onDailyCheck();

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        YearMonth mesFechamento = YearMonth.from(hoje);
        assertThat(YearMonth.from(fatura.getDataVencimento())).isEqualTo(mesFechamento);
    }

    @Test
    void vencimentoIgualFechamento_mesmoMesMesmoDia() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, hoje);

        criarItem(u.token(), cartaoId, BigDecimal.valueOf(50));
        agendador.onDailyCheck();

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        assertThat(fatura.getDataVencimento()).isEqualTo(LocalDate.now());
    }

    @Test
    void pagarFatura_eOUnicoMomentoQueSaldoCai() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        int hoje = LocalDate.now().getDayOfMonth();
        Long cartaoId = criarCartao(u.token(), u.contaId(), hoje, proximoDiaValido(hoje));
        criarItem(u.token(), cartaoId, BigDecimal.valueOf(120));

        agendador.onDailyCheck();
        assertThat(contaRepository.findById(u.contaId()).orElseThrow().getSaldo()).isEqualByComparingTo("1000");

        FaturaDTO fatura = listarFaturas(u.token(), cartaoId).get(0);
        ResponseEntity<Object> respostaPagar = patch("/api/transacoes/" + fatura.getTransacaoDespesaId() + "/pagar",
                null, u.token(), Object.class);
        assertThat(respostaPagar.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(contaRepository.findById(u.contaId()).orElseThrow().getSaldo()).isEqualByComparingTo("880");
    }

    // ---------- helpers ----------

    private int proximoDiaValido(int diaFechamento) {
        return diaFechamento >= 28 ? 5 : diaFechamento + 3 > 31 ? 31 : diaFechamento + 3;
    }

    private ItemFaturaDTO criarItem(String token, Long cartaoId, BigDecimal valor) {
        return criarItemComData(token, cartaoId, valor, LocalDate.now());
    }

    private ItemFaturaDTO criarItemComData(String token, Long cartaoId, BigDecimal valor, LocalDate data) {
        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setCartaoId(cartaoId);
        dto.setValor(valor);
        dto.setDescricao("item teste");
        dto.setData(data);
        ResponseEntity<List<ItemFaturaDTO>> resposta = post("/api/itens-fatura", dto, token,
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get(0);
    }

}
