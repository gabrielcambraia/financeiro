package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.PrimeiraEntidadeDTO;
import com.financeiro.dto.RequisicaoCriarEntidade;
import com.financeiro.dto.RequisicaoRegistro;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaEntidade;
import com.financeiro.entity.enums.TipoConta;
import com.financeiro.entity.enums.TipoPessoa;
import com.financeiro.seguranca.LimitadorTaxa;
import com.financeiro.service.ServicoIndiceEconomico;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Base compartilhada por toda a suíte de testes de integração HTTP
 * ({@code @SpringBootTest(RANDOM_PORT)} + {@code TestRestTemplate}). Sobe um
 * único container Postgres, estático, para a JVM inteira — iniciado num
 * bloco {@code static} (sem {@code @Testcontainers}/{@code @Container}) para
 * não ser parado ao fim de cada classe; o Ryuk do Testcontainers cuida da
 * limpeza no fim da JVM. Isso também faz com que o Spring reaproveite um
 * único {@code ApplicationContext} entre todas as subclasses, já que a
 * configuração de propriedades é idêntica.
 *
 * Isolamento entre testes continua sendo por dados novos (e-mail com
 * {@code UUID.randomUUID()} a cada registro cria um espaço novo), não por
 * rollback — logo qualquer asserção agregada (ex. {@code findAll}) precisa
 * ser filtrada por {@code espacoId} ou pelos recursos criados no próprio
 * teste, nunca assumir banco vazio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class TesteIntegracaoBase {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("financeiro")
            .withUsername("financeiro")
            .withPassword("financeiro");

    static {
        POSTGRES.start();
    }

    // Chave de 32 zero-bytes em base64 — válida para AES-256-GCM em testes.
    private static final String CHAVE_TESTE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("financeiro.jwt.segredo", () -> "segredo-de-teste-com-pelo-menos-32-bytes-1234567890");
        registry.add("financeiro.cookie.seguro", () -> "false");
        registry.add("financeiro.cifragem.chave", () -> CHAVE_TESTE);
        registry.add("financeiro.cifragem.chave-hmac", () -> CHAVE_TESTE);
        registry.add("spring.mail.host", () -> "localhost");
    }

    @LocalServerPort
    protected int porta;

    @Autowired
    protected TestRestTemplate restTemplate;

    // O container Postgres (e o contexto Spring) agora é único para toda a suíte,
    // então o LimitadorTaxa em memória de /api/auth/login e /register também é
    // compartilhado entre classes — dezenas de registros por execução estourariam
    // o limite real (10 tentativas/60s) sem ligação com o que cada teste verifica.
    // Substituído por um mock sempre permissivo; reset por padrão a cada método
    // de teste, então reafirma o stub em @BeforeEach.
    @MockBean
    protected LimitadorTaxa limitadorTaxa;

    // AgendadorRendimento dispara ServicoIndiceEconomico.atualizarTodos() no
    // ApplicationReadyEvent do startup — ou seja, na primeira subida do contexto
    // Spring desta suíte, o que bateria na API real do Banco Central se o bean não
    // fosse mockado aqui. Um @MockBean de método void é no-op por padrão, então
    // fica só sem efeito nenhum teste precisa fazer nada a mais.
    @MockBean
    protected ServicoIndiceEconomico servicoIndiceEconomico;

    @MockBean
    protected JavaMailSender mailSender;

    @BeforeEach
    void permitirAutenticacaoSemLimiteDeTaxa() {
        when(limitadorTaxa.permitir(anyString())).thenReturn(true);
    }

    protected record Usuario(String token, Long usuarioId, Long espacoId, Long contaId) {
    }

    protected String url(String caminho) {
        return "http://localhost:" + porta + caminho;
    }

    protected HttpHeaders autenticado(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** Registra um usuário novo (e-mail aleatório) e devolve só o token. */
    protected String registrar() {
        return registrarCompleto("Usuária Teste", "usuaria" + UUID.randomUUID() + "@teste.com").getToken();
    }

    protected String registrar(String nome, String email) {
        return registrarCompleto(nome, email).getToken();
    }

    protected RespostaAutenticacao registrarCompleto(String nome, String email) {
        PrimeiraEntidadeDTO entidade = new PrimeiraEntidadeDTO();
        entidade.setTipoPessoa(TipoPessoa.FISICA);
        entidade.setNome(nome);
        // CPF válido gerado para testes — dígitos verificadores corretos.
        entidade.setDocumento("529.982.247-25");

        RequisicaoRegistro requisicao = new RequisicaoRegistro();
        requisicao.setNome(nome);
        requisicao.setEmail(email);
        requisicao.setSenha("senha12345");
        requisicao.setEntidade(entidade);

        ResponseEntity<RespostaAutenticacao> resposta = restTemplate.postForEntity(
                url("/api/auth/register"), requisicao, RespostaAutenticacao.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    /** Registra um usuário novo e já cria uma conta corrente com o saldo informado. */
    protected Usuario registrarComConta(BigDecimal saldoInicial) {
        RespostaAutenticacao corpo = registrarCompleto("Usuária Teste", "usuaria" + UUID.randomUUID() + "@teste.com");
        Long contaId = criarConta(corpo.getToken(), saldoInicial);
        return new Usuario(corpo.getToken(), corpo.getUsuarioId(), corpo.getEspacoId(), contaId);
    }

    protected Long criarConta(String token, BigDecimal saldoInicial) {
        ContaDTO dto = new ContaDTO();
        dto.setNome("Conta Teste");
        dto.setTipo(TipoConta.CORRENTE);
        dto.setSaldoInicial(saldoInicial);
        dto.setCor("#6366f1");
        dto.setIcone("wallet");
        ResponseEntity<ContaDTO> resposta = post("/api/contas", dto, token, ContaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().getId();
    }

    /** Cria uma segunda entidade no espaço do token e devolve o ID. */
    protected Long criarEntidade(String token, String nome, TipoPessoa tipoPessoa) {
        RequisicaoCriarEntidade req = new RequisicaoCriarEntidade();
        req.setTipoPessoa(tipoPessoa);
        req.setNome(nome);
        req.setDocumento(tipoPessoa == TipoPessoa.FISICA ? "529.982.247-25" : "11.222.333/0001-81");
        ResponseEntity<RespostaEntidade> resp = post("/api/entidades", req, token, RespostaEntidade.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().id();
    }

    // ---------- atalhos HTTP tipados sobre TestRestTemplate ----------

    protected HttpHeaders autenticadoComEntidade(String token, Long entidadeId) {
        HttpHeaders headers = autenticado(token);
        headers.set("X-Entidade-Id", String.valueOf(entidadeId));
        return headers;
    }

    protected <T> ResponseEntity<T> get(String caminho, String token, Class<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.GET, new HttpEntity<>(autenticado(token)), tipo);
    }

    protected <T> ResponseEntity<T> get(String caminho, String token, ParameterizedTypeReference<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.GET, new HttpEntity<>(autenticado(token)), tipo);
    }

    protected <T> ResponseEntity<T> get(String caminho, String token, Long entidadeId, Class<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.GET, new HttpEntity<>(autenticadoComEntidade(token, entidadeId)), tipo);
    }

    protected <T> ResponseEntity<T> get(String caminho, String token, Long entidadeId, ParameterizedTypeReference<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.GET, new HttpEntity<>(autenticadoComEntidade(token, entidadeId)), tipo);
    }

    protected <T> ResponseEntity<T> post(String caminho, Object corpo, String token, Class<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, autenticado(token)), tipo);
    }

    protected <T> ResponseEntity<T> post(String caminho, Object corpo, String token, ParameterizedTypeReference<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, autenticado(token)), tipo);
    }

    protected <T> ResponseEntity<T> post(String caminho, Object corpo, String token, Long entidadeId, Class<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, autenticadoComEntidade(token, entidadeId)), tipo);
    }

    protected <T> ResponseEntity<T> put(String caminho, Object corpo, String token, Class<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.PUT, new HttpEntity<>(corpo, autenticado(token)), tipo);
    }

    protected <T> ResponseEntity<T> patch(String caminho, Object corpo, String token, Class<T> tipo) {
        return restTemplate.exchange(url(caminho), HttpMethod.PATCH, new HttpEntity<>(corpo, autenticado(token)), tipo);
    }

    protected ResponseEntity<Void> delete(String caminho, String token) {
        return restTemplate.exchange(url(caminho), HttpMethod.DELETE, new HttpEntity<>(autenticado(token)), Void.class);
    }

    /** Variante de delete/patch/post/put que devolve o corpo como Map, para inspecionar erros (status + "mensagem"). */
    @SuppressWarnings("rawtypes")
    protected ResponseEntity<Map> deleteComCorpo(String caminho, String token) {
        return restTemplate.exchange(url(caminho), HttpMethod.DELETE, new HttpEntity<>(autenticado(token)), Map.class);
    }

    @SuppressWarnings("rawtypes")
    protected ResponseEntity<Map> postComCorpoDeErro(String caminho, Object corpo, String token) {
        return restTemplate.exchange(url(caminho), HttpMethod.POST, new HttpEntity<>(corpo, autenticado(token)), Map.class);
    }

    @SuppressWarnings("rawtypes")
    protected ResponseEntity<Map> patchComCorpoDeErro(String caminho, Object corpo, String token) {
        return restTemplate.exchange(url(caminho), HttpMethod.PATCH, new HttpEntity<>(corpo, autenticado(token)), Map.class);
    }
}
