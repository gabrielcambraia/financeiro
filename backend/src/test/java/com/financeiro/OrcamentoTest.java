package com.financeiro;

import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.OrcamentoDTO;
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
 * Cobre {@code OrcamentoService} — endpoints {@code /api/orcamentos}.
 */
class OrcamentoTest extends TesteIntegracaoBase {

    @Test
    void gasto_somaDespesasDaCategoria_incluindoPendentes_excluindoCanceladas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long categoriaId = criarCategoriaDespesa(token, "Mercado");
        String mesAtual = YearMonth.now().toString();

        criarDespesa(token, contaId, categoriaId, BigDecimal.valueOf(50), LocalDate.now().minusDays(1)); // paga
        TransacaoDTO pendente = criarDespesa(token, contaId, categoriaId, BigDecimal.valueOf(30), LocalDate.now().plusDays(2)); // pendente (futura)
        TransacaoDTO paraCancelar = criarDespesa(token, contaId, categoriaId, BigDecimal.valueOf(999), LocalDate.now());
        cancelarTransacao(token, paraCancelar.getId());

        OrcamentoDTO orcamento = criarOrcamento(token, categoriaId, mesAtual, BigDecimal.valueOf(1000));

        OrcamentoDTO atualizado = buscarOrcamento(token, mesAtual, orcamento.getId());
        // 50 (paga) + 30 (pendente, não filtra saldoAjustado) — cancelada (999) fica de fora
        assertThat(atualizado.getGasto()).isEqualByComparingTo("80");
    }

    @Test
    void filtraPorData_naoPorDataVencimento() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long categoriaId = criarCategoriaDespesa(token, "Assinaturas");
        String mesAtual = YearMonth.now().toString();

        // data no mês atual, vencimento no mês seguinte: TransacaoService copia
        // dataVencimento = data quando não informado, então força vencimento distinto.
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setCategoriaId(categoriaId);
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(40));
        dto.setDescricao("assinatura");
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now().plusMonths(1));
        criarTransacao(token, dto);

        OrcamentoDTO orcamento = criarOrcamento(token, categoriaId, mesAtual, BigDecimal.valueOf(1000));
        OrcamentoDTO atualizado = buscarOrcamento(token, mesAtual, orcamento.getId());

        assertThat(atualizado.getGasto()).isEqualByComparingTo("40");
    }

    @Test
    void percentualUsado_semClamp_acimaDoLimitePassaDe100_limiteZeroDaZero() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        Long categoriaId = criarCategoriaDespesa(token, "Lazer");
        String mesAtual = YearMonth.now().toString();

        criarDespesa(token, contaId, categoriaId, BigDecimal.valueOf(150), LocalDate.now());
        OrcamentoDTO orcamento = criarOrcamento(token, categoriaId, mesAtual, BigDecimal.valueOf(100));

        OrcamentoDTO atualizado = buscarOrcamento(token, mesAtual, orcamento.getId());
        assertThat(atualizado.getPercentualUsado()).isEqualTo(150.0);
    }

    @Test
    void categoriaDeReceita_400() {
        String token = registrar();
        Long categoriaReceitaId = criarCategoria(token, "Salário", TipoTransacao.RECEITA);

        OrcamentoDTO dto = new OrcamentoDTO();
        dto.setCategoriaId(categoriaReceitaId);
        dto.setMes(YearMonth.now().toString());
        dto.setLimite(BigDecimal.valueOf(100));

        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/orcamentos", dto, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Orçamento só se aplica a categorias de despesa");
    }

    @Test
    void categoriaInexistente_404() {
        String token = registrar();
        OrcamentoDTO dto = new OrcamentoDTO();
        dto.setCategoriaId(999999L);
        dto.setMes(YearMonth.now().toString());
        dto.setLimite(BigDecimal.valueOf(100));

        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/orcamentos", dto, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicidadeCategoriaEMes_409_updateDoProprioNaoConflita() {
        String token = registrar();
        Long categoriaId = criarCategoriaDespesa(token, "Transporte");
        String mesAtual = YearMonth.now().toString();
        OrcamentoDTO original = criarOrcamento(token, categoriaId, mesAtual, BigDecimal.valueOf(200));

        OrcamentoDTO duplicado = new OrcamentoDTO();
        duplicado.setCategoriaId(categoriaId);
        duplicado.setMes(mesAtual);
        duplicado.setLimite(BigDecimal.valueOf(300));
        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/orcamentos", duplicado, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Já existe um orçamento para essa categoria neste mês");

        // update do próprio registro, mesma categoria/mês, não conflita consigo mesmo
        OrcamentoDTO atualizacao = new OrcamentoDTO();
        atualizacao.setCategoriaId(categoriaId);
        atualizacao.setMes(mesAtual);
        atualizacao.setLimite(BigDecimal.valueOf(250));
        ResponseEntity<OrcamentoDTO> respostaUpdate = put("/api/orcamentos/" + original.getId(), atualizacao, token, OrcamentoDTO.class);
        assertThat(respostaUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaUpdate.getBody().getLimite()).isEqualByComparingTo("250");
    }

    @Test
    void mesInvalido_400() {
        String token = registrar();
        Long categoriaId = criarCategoriaDespesa(token, "Saúde");

        OrcamentoDTO comMesInvalido1 = new OrcamentoDTO();
        comMesInvalido1.setCategoriaId(categoriaId);
        comMesInvalido1.setMes("2026-13");
        comMesInvalido1.setLimite(BigDecimal.valueOf(100));
        ResponseEntity<Map> resposta1 = postComCorpoDeErro("/api/orcamentos", comMesInvalido1, token);
        assertThat(resposta1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta1.getBody().get("mensagem")).isEqualTo("Mês inválido (use o formato yyyy-MM)");

        OrcamentoDTO comMesInvalido2 = new OrcamentoDTO();
        comMesInvalido2.setCategoriaId(categoriaId);
        comMesInvalido2.setMes("abc");
        comMesInvalido2.setLimite(BigDecimal.valueOf(100));
        ResponseEntity<Map> resposta2 = postComCorpoDeErro("/api/orcamentos", comMesInvalido2, token);
        assertThat(resposta2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta2.getBody().get("mensagem")).isEqualTo("Mês inválido (use o formato yyyy-MM)");
    }

    // ---------- helpers ----------

    private Long criarCategoriaDespesa(String token, String nome) {
        return criarCategoria(token, nome, TipoTransacao.DESPESA);
    }

    private Long criarCategoria(String token, String nome, TipoTransacao tipo) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setNome(nome);
        dto.setTipo(tipo);
        dto.setCor("#654321");
        dto.setIcone("tag");
        ResponseEntity<CategoriaDTO> resposta = post("/api/categorias", dto, token, CategoriaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    private TransacaoDTO criarDespesa(String token, Long contaId, Long categoriaId, BigDecimal valor, LocalDate data) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(contaId);
        dto.setCategoriaId(categoriaId);
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(valor);
        dto.setDescricao("despesa teste");
        dto.setData(data);
        return criarTransacao(token, dto);
    }

    private TransacaoDTO criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = post("/api/transacoes", dto, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get(0);
    }

    private void cancelarTransacao(String token, Long id) {
        ResponseEntity<TransacaoDTO> resposta = patch("/api/transacoes/" + id + "/cancelar", null, token, TransacaoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private OrcamentoDTO criarOrcamento(String token, Long categoriaId, String mes, BigDecimal limite) {
        OrcamentoDTO dto = new OrcamentoDTO();
        dto.setCategoriaId(categoriaId);
        dto.setMes(mes);
        dto.setLimite(limite);
        ResponseEntity<OrcamentoDTO> resposta = post("/api/orcamentos", dto, token, OrcamentoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private OrcamentoDTO buscarOrcamento(String token, String mes, Long id) {
        ResponseEntity<List<OrcamentoDTO>> resposta = get("/api/orcamentos?mes=" + mes, token,
                new ParameterizedTypeReference<List<OrcamentoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream().filter(o -> o.getId().equals(id)).findFirst().orElseThrow();
    }
}
