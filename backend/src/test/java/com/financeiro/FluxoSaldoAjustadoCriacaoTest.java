package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoConta;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o comportamento de {@code TransacaoService.create()} quanto à
 * quitação na criação ({@code quitarNaCriacao}, default {@code true}):
 * transações com data já alcançada ajustam o saldo da conta imediatamente;
 * transações futuras (inclusive as pré-criadas por transações fixas e
 * parcelas) nascem {@code PENDENTES} e não mexem no saldo até serem pagas.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FluxoSaldoAjustadoCriacaoTest {

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
    void criar_fixa_geraEntradaAtualMais11Futuras() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO dto = transacao(
                contaId, TipoTransacao.DESPESA, BigDecimal.valueOf(20), LocalDate.now(), true, null);
        criarTransacao(token, dto);

        // saldo mexeu só pela entrada do mês atual
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("80");

        YearMonth mesAtual = YearMonth.now();
        assertThat(transacoesDoMes(token, mesAtual)).hasSize(1);
        for (int i = 1; i <= 11; i++) {
            List<Map> doMes = transacoesDoMes(token, mesAtual.plusMonths(i));
            assertThat(doMes).as("mês +%d", i).hasSize(1);
            assertThat(doMes.get(0).get("status")).isEqualTo("PENDENTE");
        }
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
        // parcelas de -2 e -1 meses já venceram; a do mês atual também (dataBase é passada)
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("70");
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

    private String registrar() {
        RequisicaoRegistro requisicao = new RequisicaoRegistro();
        requisicao.setNome("Usuária Teste");
        requisicao.setEmail("usuaria" + UUID.randomUUID() + "@teste.com");
        requisicao.setSenha("senha12345");

        ResponseEntity<RespostaAutenticacao> resposta = restTemplate.postForEntity(
                url("/api/auth/register"), requisicao, RespostaAutenticacao.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().getToken();
    }

    private Long criarConta(String token, BigDecimal saldoInicial) {
        ContaDTO dto = new ContaDTO();
        dto.setNome("Conta Teste");
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldo(saldoInicial);
        dto.setCor("#6366f1");
        dto.setIcone("wallet");

        ResponseEntity<ContaDTO> resposta = restTemplate.exchange(
                url("/api/contas"), HttpMethod.POST, new HttpEntity<>(dto, autenticado(token)), ContaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    private List<TransacaoDTO> criarTransacao(String token, TransacaoDTO dto) {
        ResponseEntity<List<TransacaoDTO>> resposta = restTemplate.exchange(
                url("/api/transacoes"), HttpMethod.POST, new HttpEntity<>(dto, autenticado(token)),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private BigDecimal saldoConta(String token, Long contaId) {
        ResponseEntity<List<ContaDTO>> resposta = restTemplate.exchange(
                url("/api/contas"), HttpMethod.GET, new HttpEntity<>(autenticado(token)),
                new ParameterizedTypeReference<List<ContaDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream()
                .filter(c -> c.getId().equals(contaId))
                .findFirst()
                .orElseThrow()
                .getSaldo();
    }

    @SuppressWarnings("rawtypes")
    private List<Map> transacoesDoMes(String token, YearMonth mes) {
        ResponseEntity<List<Map>> resposta = restTemplate.exchange(
                url("/api/transacoes?month=" + mes), HttpMethod.GET, new HttpEntity<>(autenticado(token)),
                new ParameterizedTypeReference<List<Map>>() {
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
