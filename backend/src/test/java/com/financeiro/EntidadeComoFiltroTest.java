package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.entity.enums.TipoConta;
import com.financeiro.entity.enums.TipoPessoa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida que o header X-Entidade-Id filtra corretamente os recursos por entidade.
 *
 * Regras:
 * - entidade_id = NULL → global, aparece em qualquer filtro
 * - entidade_id = X    → específico da entidade X
 * - sem header         → sem restrição (vê tudo)
 * - com header X       → vê entidade X + globais
 */
class EntidadeComoFiltroTest extends TesteIntegracaoBase {

    private String token;
    private Long entidadePfId;
    private Long entidadePjId;
    private Long contaPfId;
    private Long contaPjId;
    private Long contaGlobalId;

    @BeforeEach
    void setup() {
        // Registro já cria a 1ª entidade (PF) automaticamente
        var auth = registrarCompleto("Teste Filtro", "filtro+" + UUID.randomUUID() + "@teste.com");
        token = auth.getToken();

        ativarPlanoEmpresa(auth.getEspacoId());

        // A 1ª entidade criada no registro é recuperada via listagem
        ResponseEntity<List<Map>> entidades = get("/api/entidades", token,
                new ParameterizedTypeReference<List<Map>>() {});
        assertThat(entidades.getStatusCode()).isEqualTo(HttpStatus.OK);
        entidadePfId = ((Number) entidades.getBody().get(0).get("id")).longValue();

        // Cria 2ª entidade (PJ)
        entidadePjId = criarEntidade(token, "Minha PJ", TipoPessoa.JURIDICA);

        // 3 contas: PF, PJ e global (sem entidade)
        contaPfId = criarContaComEntidade(token, "Conta PF", entidadePfId);
        contaPjId = criarContaComEntidade(token, "Conta PJ", entidadePjId);
        contaGlobalId = criarContaComEntidade(token, "Conta Global", null);
    }

    @Test
    void semHeaderRetornaTodosOsRecursos() {
        List<Map> contas = listarContas(token, null);

        List<Long> ids = contas.stream()
                .map(c -> ((Number) c.get("id")).longValue())
                .toList();

        assertThat(ids).contains(contaPfId, contaPjId, contaGlobalId);
    }

    @Test
    void filtroEntidadePfRetornaContaPfEGlobal() {
        List<Map> contas = listarContas(token, entidadePfId);

        List<Long> ids = contas.stream()
                .map(c -> ((Number) c.get("id")).longValue())
                .toList();

        assertThat(ids).contains(contaPfId, contaGlobalId);
        assertThat(ids).doesNotContain(contaPjId);
    }

    @Test
    void filtroEntidadePjRetornaContaPjEGlobal() {
        List<Map> contas = listarContas(token, entidadePjId);

        List<Long> ids = contas.stream()
                .map(c -> ((Number) c.get("id")).longValue())
                .toList();

        assertThat(ids).contains(contaPjId, contaGlobalId);
        assertThat(ids).doesNotContain(contaPfId);
    }

    @Test
    void contaGlobalAparececEmTodosOsFiltros() {
        List<Long> semFiltro = extrairIds(listarContas(token, null));
        List<Long> filtradoPf = extrairIds(listarContas(token, entidadePfId));
        List<Long> filtradoPj = extrairIds(listarContas(token, entidadePjId));

        assertThat(semFiltro).contains(contaGlobalId);
        assertThat(filtradoPf).contains(contaGlobalId);
        assertThat(filtradoPj).contains(contaGlobalId);
    }

    @Test
    void entidadeIdEhRetornadoNaResposta() {
        List<Map> contas = listarContas(token, null);

        Map contaPf = contas.stream()
                .filter(c -> ((Number) c.get("id")).longValue() == contaPfId)
                .findFirst().orElseThrow();

        Map contaGlobal = contas.stream()
                .filter(c -> ((Number) c.get("id")).longValue() == contaGlobalId)
                .findFirst().orElseThrow();

        assertThat(((Number) contaPf.get("entidadeId")).longValue()).isEqualTo(entidadePfId);
        assertThat(contaGlobal.get("entidadeId")).isNull();
    }

    // ---------- helpers ----------

    private Long criarContaComEntidade(String token, String nome, Long entidadeId) {
        ContaDTO dto = new ContaDTO();
        dto.setNome(nome);
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldoInicial(BigDecimal.valueOf(100));
        dto.setCor("#6366f1");
        dto.setIcone("wallet");
        dto.setEntidadeId(entidadeId);

        ResponseEntity<ContaDTO> resp = post("/api/contas", dto, token, ContaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> listarContas(String token, Long entidadeId) {
        ResponseEntity<List<Map>> resp = entidadeId != null
                ? get("/api/contas", token, entidadeId, new ParameterizedTypeReference<List<Map>>() {})
                : get("/api/contas", token, new ParameterizedTypeReference<List<Map>>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private List<Long> extrairIds(List<Map> contas) {
        return contas.stream().map(c -> ((Number) c.get("id")).longValue()).toList();
    }
}
