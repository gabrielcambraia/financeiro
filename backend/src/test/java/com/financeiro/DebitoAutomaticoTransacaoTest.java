package com.financeiro;

import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorTransacaoFixa;
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
 * Trava o campo {@code debitoAutomatico} na criação e atualização de
 * transações via API, e a propagação da flag em séries fixas pelo
 * {@link AgendadorTransacaoFixa}.
 */
class DebitoAutomaticoTransacaoTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorTransacaoFixa agendadorFixa;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Test
    void criar_debitoAutomatico_campoRetornadoNaResposta() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO dto = despesaFutura(contaId, BigDecimal.valueOf(100));
        dto.setDebitoAutomatico(true);

        List<TransacaoDTO> criadas = criarTransacao(token, dto);

        assertThat(criadas).hasSize(1);
        assertThat(criadas.get(0).isDebitoAutomatico()).isTrue();
    }

    @Test
    void criar_semFlag_padraoFalse() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        List<TransacaoDTO> criadas = criarTransacao(token, despesaFutura(contaId, BigDecimal.valueOf(50)));

        assertThat(criadas.get(0).isDebitoAutomatico()).isFalse();
    }

    @Test
    void criar_receita_debitoAutomaticoForcadoFalse() {
        // A lógica do serviço força false para tipo != DESPESA — a flag é
        // silenciosamente ignorada, sem retornar erro.
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setTipo(TipoTransacao.RECEITA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(200));
        dto.setData(LocalDate.now().plusMonths(1));
        dto.setFixa(false);
        dto.setDebitoAutomatico(true);

        List<TransacaoDTO> criadas = criarTransacao(token, dto);

        assertThat(criadas.get(0).isDebitoAutomatico()).isFalse();
    }

    @Test
    void atualizar_ativaDebitoAutomatico() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        Long id = criarTransacao(token, despesaFutura(contaId, BigDecimal.valueOf(80))).get(0).getId();

        TransacaoDTO alteracao = despesaFutura(contaId, BigDecimal.valueOf(80));
        alteracao.setDebitoAutomatico(true);

        ResponseEntity<TransacaoDTO> resposta = put("/api/transacoes/" + id, alteracao, token, TransacaoDTO.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().isDebitoAutomatico()).isTrue();
    }

    @Test
    void atualizar_desativaDebitoAutomatico() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO comFlag = despesaFutura(contaId, BigDecimal.valueOf(80));
        comFlag.setDebitoAutomatico(true);
        Long id = criarTransacao(token, comFlag).get(0).getId();

        TransacaoDTO semFlag = despesaFutura(contaId, BigDecimal.valueOf(80));
        semFlag.setDebitoAutomatico(false);

        ResponseEntity<TransacaoDTO> resposta = put("/api/transacoes/" + id, semFlag, token, TransacaoDTO.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().isDebitoAutomatico()).isFalse();
    }

    @Test
    void atualizar_receita_debitoAutomaticoForcadoFalse() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        TransacaoDTO criacao = new TransacaoDTO();
        criacao.setContaId(contaId);
        criacao.setTipo(TipoTransacao.RECEITA);
        criacao.setTipoPagamento(TipoPagamento.DEBITO);
        criacao.setValor(BigDecimal.valueOf(50));
        criacao.setData(LocalDate.now().plusMonths(1));
        criacao.setFixa(false);
        Long id = criarTransacao(token, criacao).get(0).getId();

        TransacaoDTO alteracao = new TransacaoDTO();
        alteracao.setContaId(contaId);
        alteracao.setTipo(TipoTransacao.RECEITA);
        alteracao.setTipoPagamento(TipoPagamento.DEBITO);
        alteracao.setValor(BigDecimal.valueOf(50));
        alteracao.setData(LocalDate.now().plusMonths(1));
        alteracao.setFixa(false);
        alteracao.setDebitoAutomatico(true);

        ResponseEntity<TransacaoDTO> resposta = put("/api/transacoes/" + id, alteracao, token, TransacaoDTO.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().isDebitoAutomatico()).isFalse();
    }

    @Test
    void fixa_extensao_propagaDebitoAutomatico() {
        // Cria fixa no mês atual para que o agendador a encontre como modelo.
        // O create() pré-cria meses+1 a +11; o agendador estende para mês+12.
        // Todas as cópias devem herdar debitoAutomatico=true.
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));

        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(100));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setFixa(true);
        dto.setDebitoAutomatico(true);
        dto.setQuitarNaCriacao(true);

        criarTransacao(u.token(), dto);

        agendadorFixa.onStartup();

        // meses+1 a +12 devem ter debitoAutomatico=true
        YearMonth mesAtual = YearMonth.now();
        for (int i = 1; i <= 12; i++) {
            YearMonth mesAlvo = mesAtual.plusMonths(i);
            boolean todasComFlag = transacaoRepository
                    .findByEspacoIdAndDataBetweenOrderByDataDesc(
                            u.espacoId(), mesAlvo.atDay(1), mesAlvo.atEndOfMonth())
                    .stream()
                    .allMatch(t -> t.isDebitoAutomatico());
            assertThat(todasComFlag)
                    .as("mês +%d deve ter debitoAutomatico=true", i)
                    .isTrue();
        }
    }

    // ---------- helpers ----------

    private TransacaoDTO despesaFutura(Long contaId, BigDecimal valor) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setData(LocalDate.now().plusMonths(1));
        dto.setDataVencimento(LocalDate.now().plusMonths(1));
        dto.setFixa(false);
        dto.setQuitarNaCriacao(false);
        return dto;
    }

    private List<TransacaoDTO> criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = post("/api/transacoes", dto, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }
}
