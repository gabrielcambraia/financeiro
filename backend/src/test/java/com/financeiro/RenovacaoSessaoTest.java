package com.financeiro;

import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.entity.TokenAtualizacao;
import com.financeiro.repository.TokenAtualizacaoRepository;
import com.financeiro.scheduler.AgendadorLimpezaTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão do bug: usuário deslogado aos 15 min de login independente de
 * atividade. Causa raiz era o access token e o refresh token expirando no
 * mesmo instante — a renovação (reativa, disparada só por 401) nunca chegava
 * a tempo de deslizar a janela do refresh. Ver
 * C:\Users\gabri\.claude\plans\o-token-jwt-nao-ticklish-scone.md.
 */
class RenovacaoSessaoTest extends TesteIntegracaoBase {

    private static final String ORIGEM = "http://localhost:5173";
    private static final String NOME_COOKIE = "financeiro.refresh";

    @Value("${financeiro.jwt.validade-acesso-minutos}")
    private long validadeAcessoMinutos;

    @Value("${financeiro.token-atualizacao.validade-minutos}")
    private long validadeAtualizacaoMinutos;

    @Autowired
    private TokenAtualizacaoRepository tokenAtualizacaoRepository;

    @Autowired
    private AgendadorLimpezaTokens agendadorLimpezaTokens;

    @Test
    void accessTokenDeveExpirarBemAntesDoRefresh() {
        // Regressão direta: se os dois TTLs forem iguais (ou o access for
        // maior), o refresh já expirou no instante em que o access expira e
        // a sessão nunca desliza com o uso normal do app.
        assertThat(validadeAcessoMinutos).isLessThan(validadeAtualizacaoMinutos);
    }

    @Test
    void renovarDeveEmitirCookieNovoComExpiracaoMaior() {
        ResponseEntity<RespostaAutenticacao> registro = registrarERetornarResposta();
        String cookieOriginal = extrairCookie(registro);
        TokenAtualizacao linhaOriginal = buscarPorTokenBruto(cookieOriginal);

        ResponseEntity<RespostaAutenticacao> renovacao = renovar(cookieOriginal);
        assertThat(renovacao.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Não comparamos o JWT em si: se emitido no mesmo segundo que o
        // original, claims e `iat` idênticos produzem o mesmo token — não é
        // bug, só falta de granularidade sub-segundo. O cookie de refresh
        // (aleatório a cada emissão) e a linha nova no banco já provam a
        // rotação.
        String cookieNovo = extrairCookie(renovacao);
        assertThat(cookieNovo).isNotEqualTo(cookieOriginal);

        TokenAtualizacao linhaNova = buscarPorTokenBruto(cookieNovo);
        assertThat(linhaNova.getExpiraEm()).isAfter(linhaOriginal.getExpiraEm());
        assertThat(linhaNova.isRevogado()).isFalse();
    }

    @Test
    void renovacoesEncadeadasDevemManterSessaoViva() {
        ResponseEntity<RespostaAutenticacao> registro = registrarERetornarResposta();
        String cookie = extrairCookie(registro);

        for (int i = 0; i < 3; i++) {
            ResponseEntity<RespostaAutenticacao> renovacao = renovar(cookie);
            assertThat(renovacao.getStatusCode()).isEqualTo(HttpStatus.OK);
            cookie = extrairCookie(renovacao);
        }
    }

    @Test
    void reenviarCookieJaRotacionadoDentroDaJanelaDeGracaNaoDerrubaSessao() {
        ResponseEntity<RespostaAutenticacao> registro = registrarERetornarResposta();
        String cookieOriginal = extrairCookie(registro);

        ResponseEntity<RespostaAutenticacao> primeiraRenovacao = renovar(cookieOriginal);
        String cookieNovo = extrairCookie(primeiraRenovacao);

        // Reenviar o cookie já substituído simula duas abas renovando quase
        // ao mesmo tempo: dentro da janela de graça isso é 401 "Sessão sendo
        // renovada", não roubo — não deve revogar a sessão vencedora.
        ResponseEntity<Map> corridaPerdida = renovarComCorpoDeErro(cookieOriginal);
        assertThat(corridaPerdida.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<RespostaAutenticacao> segundaRenovacao = renovar(cookieNovo);
        assertThat(segundaRenovacao.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void limpezaDeveRemoverApenasTokensExpiradosHaMaisDeUmDia() {
        Long usuarioId = registrarERetornarResposta().getBody().getUsuarioId();
        Long espacoId = registrarERetornarResposta().getBody().getEspacoId();
        LocalDateTime agora = LocalDateTime.now();

        TokenAtualizacao expirado = tokenAtualizacaoRepository.save(TokenAtualizacao.builder()
                .tokenHash("hash-expirado-" + agora)
                .usuarioId(usuarioId)
                .espacoId(espacoId)
                .criadoEm(agora.minusDays(3))
                .expiraEm(agora.minusDays(2))
                .build());

        TokenAtualizacao valido = tokenAtualizacaoRepository.save(TokenAtualizacao.builder()
                .tokenHash("hash-valido-" + agora)
                .usuarioId(usuarioId)
                .espacoId(espacoId)
                .criadoEm(agora)
                .expiraEm(agora.plusMinutes(15))
                .build());

        agendadorLimpezaTokens.limpar();

        assertThat(tokenAtualizacaoRepository.findById(expirado.getId())).isEmpty();
        assertThat(tokenAtualizacaoRepository.findById(valido.getId())).isPresent();
    }

    // ---------- helpers ----------

    private ResponseEntity<RespostaAutenticacao> registrarERetornarResposta() {
        return registrarCompleto("http://localhost:" + porta);
    }

    private ResponseEntity<RespostaAutenticacao> registrarCompleto(String base) {
        com.financeiro.dto.PrimeiraFilialDTO entidade = new com.financeiro.dto.PrimeiraFilialDTO();
        entidade.setTipoPessoa(com.financeiro.entity.enums.TipoPessoa.FISICA);
        entidade.setNome("Usuária Teste");
        entidade.setDocumento("529.982.247-25");

        com.financeiro.dto.RequisicaoRegistro requisicao = new com.financeiro.dto.RequisicaoRegistro();
        requisicao.setNome("Usuária Teste");
        requisicao.setEmail("usuaria" + java.util.UUID.randomUUID() + "@teste.com");
        requisicao.setSenha("senha12345");
        requisicao.setEntidade(entidade);
        return restTemplate.postForEntity(url("/api/auth/register"), requisicao, RespostaAutenticacao.class);
    }

    private ResponseEntity<RespostaAutenticacao> renovar(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, ORIGEM);
        headers.set(HttpHeaders.COOKIE, NOME_COOKIE + "=" + cookie);
        return restTemplate.exchange(url("/api/auth/renovar"), HttpMethod.POST,
                new HttpEntity<>(headers), RespostaAutenticacao.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> renovarComCorpoDeErro(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, ORIGEM);
        headers.set(HttpHeaders.COOKIE, NOME_COOKIE + "=" + cookie);
        return restTemplate.exchange(url("/api/auth/renovar"), HttpMethod.POST,
                new HttpEntity<>(headers), Map.class);
    }

    private String extrairCookie(ResponseEntity<?> resposta) {
        List<String> cookies = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        for (String cabecalho : cookies) {
            if (cabecalho.startsWith(NOME_COOKIE + "=")) {
                String semNome = cabecalho.substring((NOME_COOKIE + "=").length());
                return semNome.substring(0, semNome.indexOf(';'));
            }
        }
        throw new AssertionError("Cookie " + NOME_COOKIE + " não encontrado em: " + cookies);
    }

    private TokenAtualizacao buscarPorTokenBruto(String tokenBruto) {
        String hash = sha256Hex(tokenBruto);
        return tokenAtualizacaoRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AssertionError("Token não encontrado para hash " + hash));
    }

    private String sha256Hex(String valor) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
