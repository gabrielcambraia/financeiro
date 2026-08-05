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
 * Garante que o FiltroEntidadeAtual rejeita corretamente headers inválidos:
 * - Entidade de outro espaço (cross-tenant) → 403
 * - Entidade inexistente → 403
 * - Valor não-numérico → 400
 */
class FiltroEntidadeCrossTenantTest extends TesteIntegracaoBase {

    private String tokenA;
    private Long entidadeDeA;
    private String tokenB;

    @BeforeEach
    void setup() {
        var authA = registrarCompleto("Usuário A", "a+" + UUID.randomUUID() + "@teste.com");
        tokenA = authA.getToken();

        var authB = registrarCompleto("Usuário B", "b+" + UUID.randomUUID() + "@teste.com");
        tokenB = authB.getToken();

        // Entidade criada no espaço de A
        entidadeDeA = criarEntidade(tokenA, "PJ do A", TipoPessoa.JURIDICA);
    }

    @Test
    void headerComEntidadeDeOutroEspacoRetorna403() {
        // B tenta usar a entidade que pertence ao espaço de A
        ResponseEntity<Map> resp = getComHeaderRaw("/api/contas", tokenB, String.valueOf(entidadeDeA));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void headerComEntidadeInexistenteRetorna403() {
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
    void rotaDeEntidadesIgnoraOHeader() {
        // /api/entidades não deve ser filtrada por X-Entidade-Id
        // (o filtro do backend pula rotas que começam com /api/entidades)
        ResponseEntity<List<Map>> resp = get("/api/entidades", tokenA, entidadeDeA,
                new ParameterizedTypeReference<List<Map>>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- helper ----------

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> getComHeaderRaw(String caminho, String token, String valorHeader) {
        HttpHeaders headers = autenticado(token);
        headers.set("X-Entidade-Id", valorHeader);
        return restTemplate.exchange(url(caminho), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
