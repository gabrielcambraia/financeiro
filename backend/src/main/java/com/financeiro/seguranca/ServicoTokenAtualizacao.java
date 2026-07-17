package com.financeiro.seguranca;

import com.financeiro.entity.TokenAtualizacao;
import com.financeiro.repository.TokenAtualizacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Emite, rotaciona e revoga refresh tokens opacos. O valor bruto só existe
 * em memória entre a criação e a resposta HTTP — o banco guarda apenas o
 * hash SHA-256. Sessão é única por usuário: {@link #emitir} sempre revoga
 * qualquer token ativo anterior antes de criar um novo.
 *
 * <p>Reuso de um token já revogado/expirado em {@link #rotacionar} é tratado
 * como indício de roubo: todos os tokens ativos do usuário são derrubados,
 * forçando login novo em todos os dispositivos.
 */
@Service
public class ServicoTokenAtualizacao {

    private static final SecureRandom GERADOR_ALEATORIO = new SecureRandom();

    // Janela em que um token recém-substituído ainda pode aparecer numa
    // segunda requisição de renovação legítima (ex.: duas abas fazendo
    // bootstrap ao mesmo tempo) sem ser tratado como roubo.
    private static final long GRACA_ROTACAO_SEGUNDOS = 30;

    private final TokenAtualizacaoRepository repository;
    private final long validadeMinutos;

    public ServicoTokenAtualizacao(
            TokenAtualizacaoRepository repository,
            @Value("${financeiro.token-atualizacao.validade-minutos:15}") long validadeMinutos) {
        this.repository = repository;
        this.validadeMinutos = validadeMinutos;
    }

    @Transactional
    public TokenRenovado emitir(Long usuarioId, Long espacoId, String userAgent) {
        repository.revogarTodosDoUsuario(usuarioId, LocalDateTime.now());
        return criarToken(usuarioId, espacoId, userAgent);
    }

    // noRollbackFor é essencial aqui: ao detectar reuso, revogamos todos os
    // tokens do usuário e então lançamos 401 — sem isso, o rollback padrão
    // do Spring para RuntimeException desfaria a própria revogação que
    // estamos tentando persistir.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenRenovado rotacionar(String tokenBruto, String userAgent) {
        String hash = hash(tokenBruto);
        TokenAtualizacao atual = repository.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));

        LocalDateTime agora = LocalDateTime.now();

        if (atual.isRevogado()) {
            boolean corridaLegitima = atual.getSubstituidoPor() != null
                    && atual.getRevogadoEm() != null
                    && atual.getRevogadoEm().plusSeconds(GRACA_ROTACAO_SEGUNDOS).isAfter(agora);
            if (corridaLegitima) {
                // Outra requisição (ex.: outra aba) já rotacionou este token
                // dentro da janela de graça. Não é reuso malicioso: apenas
                // esta chamada perde a corrida, sem derrubar as demais sessões.
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão sendo renovada");
            }
            repository.revogarTodosDoUsuario(atual.getUsuarioId(), agora);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida");
        }

        if (atual.getExpiraEm().isBefore(agora)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão expirada");
        }

        TokenRenovado novo = criarToken(atual.getUsuarioId(), atual.getEspacoId(), userAgent);

        atual.setRevogado(true);
        atual.setRevogadoEm(LocalDateTime.now());
        atual.setSubstituidoPor(hash(novo.tokenBruto()));
        repository.save(atual);

        return novo;
    }

    @Transactional
    public void revogar(String tokenBruto) {
        repository.findByTokenHash(hash(tokenBruto)).ifPresent(token -> {
            token.setRevogado(true);
            token.setRevogadoEm(LocalDateTime.now());
            repository.save(token);
        });
    }

    @Transactional
    public void revogarTodosDoUsuario(Long usuarioId) {
        repository.revogarTodosDoUsuario(usuarioId, LocalDateTime.now());
    }

    public long validadeSegundos() {
        return validadeMinutos * 60;
    }

    private TokenRenovado criarToken(Long usuarioId, Long espacoId, String userAgent) {
        byte[] bytesAleatorios = new byte[32];
        GERADOR_ALEATORIO.nextBytes(bytesAleatorios);
        String bruto = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);

        LocalDateTime agora = LocalDateTime.now();
        repository.save(TokenAtualizacao.builder()
                .tokenHash(hash(bruto))
                .usuarioId(usuarioId)
                .espacoId(espacoId)
                .criadoEm(agora)
                .expiraEm(agora.plusMinutes(validadeMinutos))
                .userAgent(userAgent)
                .build());

        return new TokenRenovado(bruto, usuarioId, espacoId);
    }

    private String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
