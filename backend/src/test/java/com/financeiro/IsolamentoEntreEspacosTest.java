package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.entity.enums.TipoConta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IsolamentoEntreEspacosTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("financeiro")
            .withUsername("financeiro")
            .withPassword("financeiro");

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("financeiro.jwt.segredo", () -> "segredo-de-teste-com-pelo-menos-32-bytes-1234567890");
        registry.add("financeiro.cookie.seguro", () -> "false");
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private TestRestTemplate restTemplate;

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

        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/api/contas/" + contaIdA), HttpMethod.PUT,
                new HttpEntity<>(alteracaoMaliciosa, autenticado(tokenB)), String.class);

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

        ResponseEntity<String> resposta = restTemplate.exchange(
                url("/api/contas/" + contaIdA), HttpMethod.DELETE,
                new HttpEntity<>(autenticado(tokenB)), String.class);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(listarContas(tokenA)).extracting(c -> ((Number) c.get("id")).longValue())
                .contains(contaIdA);
    }

    private String registrar(String nome, String email) {
        RequisicaoRegistro requisicao = new RequisicaoRegistro();
        requisicao.setNome(nome);
        requisicao.setEmail(email);
        requisicao.setSenha("senha12345");

        ResponseEntity<RespostaAutenticacao> resposta = restTemplate.postForEntity(
                url("/api/auth/register"), requisicao, RespostaAutenticacao.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().getToken();
    }

    private Long criarConta(String token, String nome) {
        ContaDTO dto = new ContaDTO();
        dto.setNome(nome);
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldo(BigDecimal.valueOf(100));
        dto.setCor("#6366f1");
        dto.setIcone("wallet");

        ResponseEntity<ContaDTO> resposta = restTemplate.exchange(
                url("/api/contas"), HttpMethod.POST, new HttpEntity<>(dto, autenticado(token)), ContaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> listarContas(String token) {
        ResponseEntity<List<Map>> resposta = restTemplate.exchange(
                url("/api/contas"), HttpMethod.GET, new HttpEntity<>(autenticado(token)),
                new org.springframework.core.ParameterizedTypeReference<List<Map>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private HttpHeaders autenticado(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }
}
