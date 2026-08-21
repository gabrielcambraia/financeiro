package com.financeiro;

import com.financeiro.entity.enums.TipoPessoa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que o FiltroFilialAtual rejeita corretamente headers inválidos:
 * - Filial de outro espaço (cross-tenant) → 403
 * - Filial inexistente → 403
 * - Valor não-numérico → 400
 */
class FiltroFilialCrossTenantTest extends TesteIntegracaoBase {

    private String tokenA;
    private Long filialDeA;
    private String tokenB;

    @BeforeEach
    void setup() {
        var authA = registrarCompleto("Usuário A", "a+" + UUID.randomUUID() + "@teste.com");
        tokenA = authA.getToken();

        var authB = registrarCompleto("Usuário B", "b+" + UUID.randomUUID() + "@teste.com");
        tokenB = authB.getToken();

        // Filial criada no espaço de A
        ativarPlanoEmpresa(authA.getEspacoId());
        filialDeA = criarFilial(tokenA, "PJ do A", TipoPessoa.JURIDICA);
    }

    @Test
    void headerComFilialDeOutroEspacoRetorna403() {
        // B tenta usar a filial que pertence ao espaço de A
        ResponseEntity<Map> resp = getComHeaderRaw("/api/contas", tokenB, String.valueOf(filialDeA));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void headerComFilialInexistenteRetorna403() {
        long idInexistente = 999_999_999L;
        ResponseEntity<Map> resp = getComHeaderRaw("/api/contas", tokenA, String.valueOf(idInexistente));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void headerComValorNaoNumericoRetorna400() {
        ResponseEntity<Map> resp = getComHeaderRaw("/api/contas", tokenA, "nao-e-numero");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void criarContaComFilialIdDeOutroEspaco_rejeitada() {
        // B tenta criar conta apontando pra filial que pertence ao espaço de A
        com.financeiro.dto.ContaDTO dto = new com.financeiro.dto.ContaDTO();
        dto.setNome("Conta suspeita");
        dto.setTipo(com.financeiro.entity.enums.TipoConta.CORRENTE);
        dto.setSaldoInicial(java.math.BigDecimal.TEN);
        dto.setCor("#000");
        dto.setIcone("wallet");
        dto.setFilialId(filialDeA);

        ResponseEntity<Map> resp = postComCorpoDeErro("/api/contas", dto, tokenB);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
    }

    @Test
    void rotaDeFiliaisIgnoraOHeader() {
        // /api/filiais não deve ser filtrada por X-Filial-Id
        // (o filtro do backend pula rotas que começam com /api/filiais)
        ResponseEntity<List<Map>> resp = get("/api/filiais", tokenA, filialDeA,
                new ParameterizedTypeReference<List<Map>>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- helper ----------

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> getComHeaderRaw(String caminho, String token, String valorHeader) {
        HttpHeaders headers = autenticado(token);
        headers.set("X-Filial-Id", valorHeader);
        return restTemplate.exchange(url(caminho), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
