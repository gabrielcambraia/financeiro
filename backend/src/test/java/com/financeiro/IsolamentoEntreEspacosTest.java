package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.entity.enums.TipoConta;
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
 * Garante que um usuário de um espaço não consegue ler, alterar ou apagar
 * dados de outro espaço via IDOR (passar o id de um recurso alheio). Cobre a
 * lacuna apontada na revisão de segurança: o isolamento hoje depende de cada
 * repository filtrar manualmente por {@code espacoId}, sem um enforcement
 * transversal — este teste trava essa garantia contra regressão futura.
 */
class IsolamentoEntreEspacosTest extends TesteIntegracaoBase {

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void criarDoisUsuariosEmEspacosDistintos() {
        tokenA = registrar("Usuária A", "usuaria.a+" + UUID.randomUUID() + "@teste.com");
        tokenB = registrar("Usuário B", "usuario.b+" + UUID.randomUUID() + "@teste.com");
    }

    @Test
    void usuarioBNaoVeContaDoUsuarioA() {
        Long contaIdA = criarConta(tokenA, "Conta da A");

        List<Map> contasDeB = listarContas(tokenB);

        assertThat(contasDeB).extracting(c -> ((Number) c.get("id")).longValue())
                .doesNotContain(contaIdA);
    }

    @Test
    void usuarioBNaoConsegueAtualizarContaDoUsuarioA() {
        Long contaIdA = criarConta(tokenA, "Conta da A");

        ContaDTO alteracaoMaliciosa = new ContaDTO();
        alteracaoMaliciosa.setNome("Conta sequestrada");
        alteracaoMaliciosa.setTipo(TipoConta.CORRENTE);
        alteracaoMaliciosa.setSaldo(BigDecimal.ZERO);
        alteracaoMaliciosa.setCor("#000000");
        alteracaoMaliciosa.setIcone("wallet");

        ResponseEntity<String> resposta = put("/api/contas/" + contaIdA, alteracaoMaliciosa, tokenB, String.class);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isFalse();

        List<Map> contasDeA = listarContas(tokenA);
        assertThat(contasDeA)
                .filteredOn(c -> ((Number) c.get("id")).longValue() == contaIdA)
                .extracting(c -> c.get("nome"))
                .containsExactly("Conta da A");
    }

    @Test
    void usuarioBNaoConsegueApagarContaDoUsuarioA() {
        Long contaIdA = criarConta(tokenA, "Conta da A");

        ResponseEntity<Void> resposta = delete("/api/contas/" + contaIdA, tokenB);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(listarContas(tokenA)).extracting(c -> ((Number) c.get("id")).longValue())
                .contains(contaIdA);
    }

    private Long criarConta(String token, String nome) {
        ContaDTO dto = new ContaDTO();
        dto.setNome(nome);
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldo(BigDecimal.valueOf(100));
        dto.setCor("#6366f1");
        dto.setIcone("wallet");

        ResponseEntity<ContaDTO> resposta = post("/api/contas", dto, token, ContaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> listarContas(String token) {
        ResponseEntity<List<Map>> resposta = get("/api/contas", token, new ParameterizedTypeReference<List<Map>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }
}
