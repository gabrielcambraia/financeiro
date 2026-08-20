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
 * Trava o comportamento de {@code TransacaoService.create()} quanto à
 * quitação na criação ({@code quitarNaCriacao}, default {@code true}):
 * transações com data já alcançada ajustam o saldo da conta imediatamente;
 * transações futuras (inclusive as pré-criadas por transações fixas e
 * parcelas) nascem {@code PENDENTES} e não mexem no saldo até serem pagas.
 */
class FluxoSaldoAjustadoCriacaoTest extends TesteIntegracaoBase {

    @Test
    void criar_dataPassada_despesa_ajustaSaldoEMarcaAjustado() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        List<TransacaoDTO> criadas = criarTransacao(token, transacao(
                contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(50), LocalDate.now().minusDays(1), false, null));

        assertThat(criadas).hasSize(1);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("50");
    }

    @Test
    void criar_dataFutura_naoAjustaSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        criarTransacao(token, transacao(
                contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(50), LocalDate.now().plusMonths(2), false, null));

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");
    }

    @Test
    void criar_dataPassada_receita_incrementaSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        criarTransacao(token, transacao(
                contaId, TipoTransacao.RECEITA, BigDecimal.valueOf(30), LocalDate.now().minusDays(1), false, null));

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("130");
    }

    @Test
    void criar_fixa_geraSomenteEntradaAtual() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO dto = transacao(
                contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(20), LocalDate.now(), true, null);
        List<TransacaoDTO> criadas = criarTransacao(token, dto);

        // apenas 1 entrada criada — pré-criação de meses futuros removida (substituída por Recorrências)
        assertThat(criadas).hasSize(1);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("80");

        YearMonth mesAtual = YearMonth.now();
        assertThat(transacoesDoMes(token, mesAtual)).hasSize(1);
        // meses futuros não são mais pré-criados automaticamente
        assertThat(transacoesDoMes(token, mesAtual.plusMonths(1))).isEmpty();
    }

    @Test
    void criar_parcelada_3x_iniciandoNoPassado() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        LocalDate dataBase = LocalDate.now().minusMonths(2);
        TransacaoDTO dto = transacao(
                contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(10), dataBase, false, 3);
        List<TransacaoDTO> criadas = criarTransacao(token, dto);

        assertThat(criadas).hasSize(3);
        // O valor informado (10) é o total da compra, dividido pelas 3 parcelas
        // (3.33 + 3.33 + 3.34) — não 10 repetido em cada uma.
        assertThat(criadas.get(0).getValor()).isEqualByComparingTo("3.33");
        assertThat(criadas.get(1).getValor()).isEqualByComparingTo("3.33");
        assertThat(criadas.get(2).getValor()).isEqualByComparingTo("3.34");
        // parcelas de -2 e -1 meses já venceram; a do mês atual também (dataBase é passada)
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("90");
    }

    @Test
    void criar_parcelada_todasFuturas_naoMexeSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        LocalDate dataBase = LocalDate.now().plusMonths(1);
        TransacaoDTO dto = transacao(
                contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(10), dataBase, false, 3);
        List<TransacaoDTO> criadas = criarTransacao(token, dto);

        assertThat(criadas).hasSize(3);
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("100");
    }

    private TransacaoDTO transacao(Long contaId, TipoTransacao tipo, BigDecimal valor, LocalDate data,
                                    boolean fixa, Integer totalParcelas) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setTipo(tipo);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setDescricao("teste");
        dto.setData(data);
        dto.setFixa(fixa);
        dto.setTotalParcelas(totalParcelas);
        return dto;
    }

    private List<TransacaoDTO> criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = post("/api/transacoes", dto, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> transacoesDoMes(String token, YearMonth mes) {
        ResponseEntity<List<Map>> resposta = get("/api/transacoes?month=" + mes, token,
                new ParameterizedTypeReference<List<Map>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }
}
