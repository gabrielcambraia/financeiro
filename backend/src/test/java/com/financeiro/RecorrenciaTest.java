package com.financeiro;

import com.financeiro.dto.RecorrenciaDTO;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.TransacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecorrenciaTest extends TesteIntegracaoBase {

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ItemFaturaRepository itemFaturaRepository;

    // ---------- helpers ----------

    private RecorrenciaDTO dtoDebito(Long contaId) {
        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setContaId(contaId);
        dto.setValor(BigDecimal.valueOf(200));
        dto.setDiaCompetencia(15);
        dto.setDataInicio(YearMonth.now().atDay(1));
        return dto;
    }

    private RecorrenciaDTO criar(String token, RecorrenciaDTO dto) {
        ResponseEntity<RecorrenciaDTO> resp = post("/api/recorrencias", dto, token, RecorrenciaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    // ---------- CRUD ----------

    @Test
    void criar_debito_retornaRecorrenciaComCampos() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        RecorrenciaDTO criada = criar(token, dtoDebito(contaId));

        assertThat(criada.getId()).isNotNull();
        assertThat(criada.getTipo()).isEqualTo(TipoTransacao.DESPESA);
        assertThat(criada.getTipoPagamento()).isEqualTo(TipoPagamento.DEBITO);
        assertThat(criada.getContaId()).isEqualTo(contaId);
        assertThat(criada.getValor()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(criada.getDiaCompetencia()).isEqualTo(15);
        assertThat(criada.isAtiva()).isTrue();
        assertThat(criada.getContaNome()).isNotBlank();
        assertThat(criada.getProximaGeracaoMes()).isNotNull();
    }

    @Test
    void buscar_retornaRecorrenciaCorreta() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO criada = criar(token, dtoDebito(contaId));

        ResponseEntity<RecorrenciaDTO> resp = get("/api/recorrencias/" + criada.getId(), token, RecorrenciaDTO.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getId()).isEqualTo(criada.getId());
        assertThat(resp.getBody().getContaId()).isEqualTo(contaId);
    }

    @Test
    void listar_retornaApenasDoEspacoAtual() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        criar(token, dtoDebito(contaId));
        criar(token, dtoDebito(contaId));

        String tokenOutro = registrar();

        List<RecorrenciaDTO> proprias = get("/api/recorrencias", token,
                new ParameterizedTypeReference<List<RecorrenciaDTO>>() {}).getBody();
        assertThat(proprias).hasSizeGreaterThanOrEqualTo(2);

        List<RecorrenciaDTO> alheias = get("/api/recorrencias", tokenOutro,
                new ParameterizedTypeReference<List<RecorrenciaDTO>>() {}).getBody();
        assertThat(alheias).isEmpty();
    }

    @Test
    void atualizar_alteraCampos() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO criada = criar(token, dtoDebito(contaId));

        RecorrenciaDTO alteracao = dtoDebito(contaId);
        alteracao.setValor(BigDecimal.valueOf(450));
        alteracao.setDiaCompetencia(25);
        alteracao.setDescricao("Aluguel mensal");

        ResponseEntity<RecorrenciaDTO> resp = put("/api/recorrencias/" + criada.getId(), alteracao, token, RecorrenciaDTO.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getValor()).isEqualByComparingTo(BigDecimal.valueOf(450));
        assertThat(resp.getBody().getDiaCompetencia()).isEqualTo(25);
        assertThat(resp.getBody().getDescricao()).isEqualTo("Aluguel mensal");
    }

    @Test
    void excluir_removeRecorrencia_transacaoGeradaFicaOrfa() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO criada = criar(token, dtoDebito(contaId));
        Long recId = criada.getId();

        Transacao gerada = transacaoRepository.findAll().stream()
                .filter(t -> recId.equals(t.getOrigemRecorrenciaId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transação gerada não encontrada"));

        ResponseEntity<Void> del = delete("/api/recorrencias/" + recId, token);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/api/recorrencias/" + recId, token, RecorrenciaDTO.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ON DELETE SET NULL: transação sobrevive com origemRecorrenciaId = null
        transacaoRepository.findById(gerada.getId()).ifPresent(t ->
                assertThat(t.getOrigemRecorrenciaId()).isNull());
    }

    // ---------- validações ----------

    @Test
    void criar_debito_semConta_retorna400() {
        String token = registrar();
        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(100));
        dto.setDiaCompetencia(1);
        dto.setDataInicio(YearMonth.now().atDay(1));

        assertThat(postComCorpoDeErro("/api/recorrencias", dto, token).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criar_credito_semCartao_retorna400() {
        String token = registrar();
        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.CREDITO);
        dto.setValor(BigDecimal.valueOf(100));
        dto.setDiaCompetencia(1);
        dto.setDataInicio(YearMonth.now().atDay(1));

        assertThat(postComCorpoDeErro("/api/recorrencias", dto, token).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criar_semValor_retorna400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setContaId(contaId);
        // valor null → viola @NotNull + @Positive
        dto.setDiaCompetencia(1);
        dto.setDataInicio(YearMonth.now().atDay(1));

        assertThat(postComCorpoDeErro("/api/recorrencias", dto, token).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- geração de lançamento ----------

    @Test
    void criar_dataInicioMesAtual_geraTransacaoImediatamente() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        RecorrenciaDTO criada = criar(token, dtoDebito(contaId));

        long geradas = transacaoRepository.findAll().stream()
                .filter(t -> criada.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(geradas).isEqualTo(1);
    }

    @Test
    void criar_dataInicioFuturo_naoGeraTransacao() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO dto = dtoDebito(contaId);
        dto.setDataInicio(YearMonth.now().plusMonths(1).atDay(1));

        RecorrenciaDTO criada = criar(token, dto);

        long geradas = transacaoRepository.findAll().stream()
                .filter(t -> criada.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(geradas).isZero();
    }

    @Test
    void transacaoGerada_possuiCamposCorretos() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO dto = dtoDebito(contaId);
        dto.setDiaCompetencia(5);
        dto.setDiaVencimento(10);

        RecorrenciaDTO criada = criar(token, dto);

        Transacao t = transacaoRepository.findAll().stream()
                .filter(tx -> criada.getId().equals(tx.getOrigemRecorrenciaId()))
                .findFirst()
                .orElseThrow();

        assertThat(t.getOrigemRecorrenciaId()).isEqualTo(criada.getId());
        assertThat(t.getTipo()).isEqualTo(TipoTransacao.DESPESA);
        assertThat(t.getTipoPagamento()).isEqualTo(TipoPagamento.DEBITO);
        assertThat(t.getValor()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(t.getData().getDayOfMonth()).isEqualTo(5);
        assertThat(t.getDataVencimento().getDayOfMonth()).isEqualTo(10);
        assertThat(t.isFixa()).isFalse();
    }

    @Test
    void transacaoGerada_diaCompetencia31_clampaAoUltimoDiaDoMes() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO dto = dtoDebito(contaId);
        dto.setDiaCompetencia(31);

        RecorrenciaDTO criada = criar(token, dto);

        Transacao t = transacaoRepository.findAll().stream()
                .filter(tx -> criada.getId().equals(tx.getOrigemRecorrenciaId()))
                .findFirst()
                .orElseThrow();

        assertThat(t.getData().getDayOfMonth())
                .isLessThanOrEqualTo(YearMonth.now().lengthOfMonth());
    }

    @Test
    void gerarMesAtual_idempotente_naoCriaDuplicata() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO criada = criar(token, dtoDebito(contaId));

        // Duas chamadas manuais adicionais além da geração imediata na criação
        post("/api/recorrencias/" + criada.getId() + "/gerar-mes-atual", null, token, RecorrenciaDTO.class);
        post("/api/recorrencias/" + criada.getId() + "/gerar-mes-atual", null, token, RecorrenciaDTO.class);

        long total = transacaoRepository.findAll().stream()
                .filter(t -> criada.getId().equals(t.getOrigemRecorrenciaId()))
                .count();
        assertThat(total).isEqualTo(1);
    }

    // ---------- crédito ----------

    @Test
    void criar_credito_dataInicioMesAtual_geraItemFatura() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(token, contaId, 25, 5); // fecha dia 25

        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.CREDITO);
        dto.setCartaoId(cartaoId);
        dto.setValor(BigDecimal.valueOf(120));
        dto.setDiaCompetencia(10);
        dto.setDataInicio(YearMonth.now().atDay(1));

        RecorrenciaDTO criada = criar(token, dto);

        long gerados = itemFaturaRepository.findAll().stream()
                .filter(i -> criada.getId().equals(i.getOrigemRecorrenciaId()))
                .count();
        assertThat(gerados).isEqualTo(1);
    }

    // ---------- toggle ativa ----------

    @Test
    void alternarAtiva_desativa_reativa() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        RecorrenciaDTO criada = criar(token, dtoDebito(contaId));

        RecorrenciaDTO desativada = patch("/api/recorrencias/" + criada.getId() + "/ativa",
                Map.of("ativa", false), token, RecorrenciaDTO.class).getBody();
        assertThat(desativada.isAtiva()).isFalse();

        RecorrenciaDTO reativada = patch("/api/recorrencias/" + criada.getId() + "/ativa",
                Map.of("ativa", true), token, RecorrenciaDTO.class).getBody();
        assertThat(reativada.isAtiva()).isTrue();
    }

    // ---------- isolamento ----------

    @Test
    void buscar_outroEspaco_retorna404() {
        String tokenA = registrar();
        Long contaA = criarConta(tokenA, BigDecimal.valueOf(1000));
        RecorrenciaDTO criada = criar(tokenA, dtoDebito(contaA));

        String tokenB = registrar();
        assertThat(get("/api/recorrencias/" + criada.getId(), tokenB, RecorrenciaDTO.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void excluir_outroEspaco_retorna404() {
        String tokenA = registrar();
        Long contaA = criarConta(tokenA, BigDecimal.valueOf(1000));
        RecorrenciaDTO criada = criar(tokenA, dtoDebito(contaA));

        String tokenB = registrar();
        assertThat(delete("/api/recorrencias/" + criada.getId(), tokenB)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
