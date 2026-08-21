package com.financeiro;

import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.entity.enums.TipoConta;
import com.financeiro.entity.enums.TipoPessoa;
import com.financeiro.entity.enums.TipoTransacao;
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
 * Valida que o header X-Filial-Id filtra corretamente os recursos por filial.
 *
 * Regras:
 * - filial_id = NULL → global, aparece em qualquer filtro
 * - filial_id = X    → específico da filial X
 * - sem header       → sem restrição (vê tudo)
 * - com header X     → vê filial X + globais
 */
class FilialComoFiltroTest extends TesteIntegracaoBase {

    private String token;
    private Long filialPfId;
    private Long filialPjId;
    private Long contaPfId;
    private Long contaPjId;
    private Long contaGlobalId;

    @BeforeEach
    void setup() {
        // Registro já cria a 1ª filial (PF) automaticamente
        var auth = registrarCompleto("Teste Filtro", "filtro+" + UUID.randomUUID() + "@teste.com");
        token = auth.getToken();

        ativarPlanoEmpresa(auth.getEspacoId());

        // A 1ª filial criada no registro é recuperada via listagem
        ResponseEntity<List<Map>> filiais = get("/api/filiais", token,
                new ParameterizedTypeReference<List<Map>>() {});
        assertThat(filiais.getStatusCode()).isEqualTo(HttpStatus.OK);
        filialPfId = ((Number) filiais.getBody().get(0).get("id")).longValue();

        // Cria 2ª filial (PJ)
        filialPjId = criarFilial(token, "Minha PJ", TipoPessoa.JURIDICA);

        // 3 contas: PF, PJ e global (sem filial)
        contaPfId = criarContaComFilial(token, "Conta PF", filialPfId);
        contaPjId = criarContaComFilial(token, "Conta PJ", filialPjId);
        contaGlobalId = criarContaComFilial(token, "Conta Global", null);
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
    void filtroFilialPfRetornaContaPfEGlobal() {
        List<Map> contas = listarContas(token, filialPfId);

        List<Long> ids = contas.stream()
                .map(c -> ((Number) c.get("id")).longValue())
                .toList();

        assertThat(ids).contains(contaPfId, contaGlobalId);
        assertThat(ids).doesNotContain(contaPjId);
    }

    @Test
    void filtroFilialPjRetornaContaPjEGlobal() {
        List<Map> contas = listarContas(token, filialPjId);

        List<Long> ids = contas.stream()
                .map(c -> ((Number) c.get("id")).longValue())
                .toList();

        assertThat(ids).contains(contaPjId, contaGlobalId);
        assertThat(ids).doesNotContain(contaPfId);
    }

    @Test
    void contaGlobalAparececEmTodosOsFiltros() {
        List<Long> semFiltro = extrairIds(listarContas(token, null));
        List<Long> filtradoPf = extrairIds(listarContas(token, filialPfId));
        List<Long> filtradoPj = extrairIds(listarContas(token, filialPjId));

        assertThat(semFiltro).contains(contaGlobalId);
        assertThat(filtradoPf).contains(contaGlobalId);
        assertThat(filtradoPj).contains(contaGlobalId);
    }

    @Test
    void filialIdEhRetornadoNaResposta() {
        List<Map> contas = listarContas(token, null);

        Map contaPf = contas.stream()
                .filter(c -> ((Number) c.get("id")).longValue() == contaPfId)
                .findFirst().orElseThrow();

        Map contaGlobal = contas.stream()
                .filter(c -> ((Number) c.get("id")).longValue() == contaGlobalId)
                .findFirst().orElseThrow();

        assertThat(((Number) contaPf.get("filialId")).longValue()).isEqualTo(filialPfId);
        assertThat(contaGlobal.get("filialId")).isNull();
    }

    // ---------- atualização de filialId ----------

    @Test
    void atualizarConta_alteraFilialId_filtroRefleteMudanca() {
        // Cria com filial PF
        Long contaId = criarContaComFilial(token, "Conta Migrada", filialPfId);
        assertThat(extrairIds(listarContas(token, filialPfId))).contains(contaId);
        assertThat(extrairIds(listarContas(token, filialPjId))).doesNotContain(contaId);

        // Atualiza para filial PJ
        ContaDTO update = new ContaDTO();
        update.setNome("Conta Migrada");
        update.setTipo(TipoConta.CORRENTE);
        update.setCor("#6366f1");
        update.setIcone("wallet");
        update.setFilialId(filialPjId);

        ResponseEntity<ContaDTO> resp = put("/api/contas/" + contaId, update, token, ContaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFilialId()).isEqualTo(filialPjId);

        // Filtro deve refletir a mudança
        assertThat(extrairIds(listarContas(token, filialPjId))).contains(contaId);
        assertThat(extrairIds(listarContas(token, filialPfId))).doesNotContain(contaId);
    }

    @Test
    void atualizarConta_paraGlobal_apareceTodosOsFiltros() {
        Long contaId = criarContaComFilial(token, "Conta Tornando Global", filialPfId);

        ContaDTO update = new ContaDTO();
        update.setNome("Conta Tornando Global");
        update.setTipo(TipoConta.CORRENTE);
        update.setCor("#6366f1");
        update.setIcone("wallet");
        update.setFilialId(null);

        ResponseEntity<ContaDTO> resp = put("/api/contas/" + contaId, update, token, ContaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFilialId()).isNull();

        assertThat(extrairIds(listarContas(token, filialPfId))).contains(contaId);
        assertThat(extrairIds(listarContas(token, filialPjId))).contains(contaId);
    }

    @Test
    void atualizarCategoria_alteraFilialId_filtroRefleteMudanca() {
        Long catId = criarCategoriaComFilial(token, "Categoria Migrada", filialPfId);
        assertThat(extrairIds(listarCategorias(token, filialPfId))).contains(catId);
        assertThat(extrairIds(listarCategorias(token, filialPjId))).doesNotContain(catId);

        CategoriaDTO update = new CategoriaDTO();
        update.setNome("Categoria Migrada");
        update.setTipo(TipoTransacao.DESPESA);
        update.setCor("#6366f1");
        update.setIcone("tag");
        update.setFilialId(filialPjId);

        ResponseEntity<CategoriaDTO> resp = put("/api/categorias/" + catId, update, token, CategoriaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFilialId()).isEqualTo(filialPjId);

        assertThat(extrairIds(listarCategorias(token, filialPjId))).contains(catId);
        assertThat(extrairIds(listarCategorias(token, filialPfId))).doesNotContain(catId);
    }

    @Test
    void atualizarCategoria_paraGlobal_apareceTodosOsFiltros() {
        Long catId = criarCategoriaComFilial(token, "Categoria Tornando Global", filialPfId);

        CategoriaDTO update = new CategoriaDTO();
        update.setNome("Categoria Tornando Global");
        update.setTipo(TipoTransacao.DESPESA);
        update.setCor("#6366f1");
        update.setIcone("tag");
        update.setFilialId(null);

        ResponseEntity<CategoriaDTO> resp = put("/api/categorias/" + catId, update, token, CategoriaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getFilialId()).isNull();

        assertThat(extrairIds(listarCategorias(token, filialPfId))).contains(catId);
        assertThat(extrairIds(listarCategorias(token, filialPjId))).contains(catId);
    }

    // ---------- helpers ----------

    private Long criarContaComFilial(String token, String nome, Long filialId) {
        ContaDTO dto = new ContaDTO();
        dto.setNome(nome);
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldoInicial(BigDecimal.valueOf(100));
        dto.setCor("#6366f1");
        dto.setIcone("wallet");
        dto.setFilialId(filialId);

        ResponseEntity<ContaDTO> resp = post("/api/contas", dto, token, ContaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> listarContas(String token, Long filialId) {
        ResponseEntity<List<Map>> resp = filialId != null
                ? get("/api/contas", token, filialId, new ParameterizedTypeReference<List<Map>>() {})
                : get("/api/contas", token, new ParameterizedTypeReference<List<Map>>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private Long criarCategoriaComFilial(String token, String nome, Long filialId) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setNome(nome);
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setCor("#6366f1");
        dto.setIcone("tag");
        dto.setFilialId(filialId);

        ResponseEntity<CategoriaDTO> resp = post("/api/categorias", dto, token, CategoriaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getId();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> listarCategorias(String token, Long filialId) {
        ResponseEntity<List<Map>> resp = filialId != null
                ? get("/api/categorias", token, filialId, new ParameterizedTypeReference<List<Map>>() {})
                : get("/api/categorias", token, new ParameterizedTypeReference<List<Map>>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private List<Long> extrairIds(List<Map> contas) {
        return contas.stream().map(c -> ((Number) c.get("id")).longValue()).toList();
    }
}
