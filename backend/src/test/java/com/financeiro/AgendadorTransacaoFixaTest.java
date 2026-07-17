package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoConta;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorTransacaoFixa;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o comportamento de {@link AgendadorTransacaoFixa}: a quitação de
 * transações é manual (ver {@code TransacaoService.pagar}) — o agendador não
 * ajusta mais saldo sozinho quando uma data é alcançada, ele só garante a
 * extensão da janela de 12 meses para transações fixas. Semeia estados que
 * a API nunca produz sozinha (transação passada ainda não paga) direto no
 * repositório para travar que o agendador não mexe nelas.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgendadorTransacaoFixaTest {

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

    @Autowired
    private AgendadorTransacaoFixa agendador;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Test
    void agendador_naoMaturaVencida_saldoIntacto() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        Transacao t = seedTransacao(u, LocalDate.now().minusDays(1), BigDecimal.valueOf(30), false, false);

        agendador.onStartup();

        assertThat(saldoAtual(u.contaId)).isEqualByComparingTo("100");
        assertThat(transacaoRepository.findById(t.getId()).orElseThrow().isSaldoAjustado()).isFalse();
    }

    @Test
    void maturacao_naoTocaFutura() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        seedTransacao(u, LocalDate.now().plusMonths(1), BigDecimal.valueOf(30), false, false);

        agendador.onStartup();

        assertThat(saldoAtual(u.contaId)).isEqualByComparingTo("100");
    }

    @Test
    void extensao_criaEntradasFixasFaltantes() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        seedTransacao(u, LocalDate.now(), BigDecimal.valueOf(20), true, true);

        agendador.onStartup();

        YearMonth mesAtual = YearMonth.now();
        for (int i = 1; i <= 12; i++) {
            long total = contarTransacoesDoMes(u.espacoId, mesAtual.plusMonths(i));
            assertThat(total).as("mês +%d", i).isEqualTo(1);
        }
    }

    @Test
    void extensao_idempotente_naoDuplica() {
        Usuario u = registrarComConta(BigDecimal.valueOf(100));
        seedTransacao(u, LocalDate.now(), BigDecimal.valueOf(20), true, true);

        agendador.onStartup();
        long totalAposPrimeiraRodada = transacaoRepository.findAll().stream()
                .filter(t -> u.espacoId.equals(t.getEspacoId())).count();

        agendador.onFirstOfMonth();
        long totalAposSegundaRodada = transacaoRepository.findAll().stream()
                .filter(t -> u.espacoId.equals(t.getEspacoId())).count();

        assertThat(totalAposSegundaRodada).isEqualTo(totalAposPrimeiraRodada);
    }

    @Test
    void agendador_naoMatura_multiplosEspacos() {
        Usuario a = registrarComConta(BigDecimal.valueOf(100));
        Usuario b = registrarComConta(BigDecimal.valueOf(200));

        seedTransacao(a, LocalDate.now().minusDays(1), BigDecimal.valueOf(10), false, false);
        seedTransacao(b, LocalDate.now().minusDays(1), BigDecimal.valueOf(50), false, false);

        agendador.onStartup();

        assertThat(saldoAtual(a.contaId)).isEqualByComparingTo("100");
        assertThat(saldoAtual(b.contaId)).isEqualByComparingTo("200");
    }

    private record Usuario(String token, Long usuarioId, Long espacoId, Long contaId) {
    }

    private Usuario registrarComConta(BigDecimal saldoInicial) {
        RequisicaoRegistro requisicao = new RequisicaoRegistro();
        requisicao.setNome("Usuária Teste");
        requisicao.setEmail("usuaria" + UUID.randomUUID() + "@teste.com");
        requisicao.setSenha("senha12345");

        ResponseEntity<RespostaAutenticacao> resposta = restTemplate.postForEntity(
                url("/api/auth/register"), requisicao, RespostaAutenticacao.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        RespostaAutenticacao corpo = resposta.getBody();

        ContaDTO dto = new ContaDTO();
        dto.setNome("Conta Teste");
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldo(saldoInicial);
        dto.setCor("#6366f1");
        dto.setIcone("wallet");
        ResponseEntity<ContaDTO> respostaConta = restTemplate.exchange(
                url("/api/contas"), HttpMethod.POST, new HttpEntity<>(dto, autenticado(corpo.getToken())), ContaDTO.class);
        assertThat(respostaConta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        return new Usuario(corpo.getToken(), corpo.getUsuarioId(), corpo.getEspacoId(), respostaConta.getBody().getId());
    }

    private Transacao seedTransacao(Usuario u, LocalDate data, BigDecimal valor, boolean fixa, boolean saldoAjustado) {
        Conta conta = contaRepository.findById(u.contaId).orElseThrow();
        Transacao t = Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(valor)
                .descricao("seed")
                .data(data)
                .fixa(fixa)
                .saldoAjustado(saldoAjustado)
                .espacoId(u.espacoId)
                .usuarioId(u.usuarioId)
                .build();
        return transacaoRepository.save(t);
    }

    private BigDecimal saldoAtual(Long contaId) {
        return contaRepository.findById(contaId).orElseThrow().getSaldo();
    }

    private long contarTransacoesDoMes(Long espacoId, YearMonth mes) {
        return transacaoRepository.findByEspacoIdAndDataBetweenOrderByDataDesc(
                espacoId, mes.atDay(1), mes.atEndOfMonth()).size();
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
