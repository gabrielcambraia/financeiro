package com.financeiro.seguranca;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Gera e valida os tokens JWT usados no perfil "nuvem". O token carrega
 * {@code usuarioId} e {@code espacoId} — é a partir dessas claims que
 * {@link ContextoEspacoSeguranca} resolve o tenant ativo da requisição.
 */
@Service
@Profile("nuvem")
public class ServicoJwt {

    private final SecretKey chave;
    private final long validadeHoras;

    public ServicoJwt(
            @Value("${financeiro.jwt.segredo}") String segredo,
            @Value("${financeiro.jwt.validade-horas:720}") long validadeHoras) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validadeHoras = validadeHoras;
    }

    public String gerarToken(Long usuarioId, Long espacoId, String email, boolean precisaTrocarSenha) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("espacoId", espacoId)
                .claim("email", email)
                .claim("precisaTrocarSenha", precisaTrocarSenha)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(validadeHoras * 3600)))
                .signWith(chave)
                .compact();
    }

    public UsuarioAutenticado validarEExtrair(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Long usuarioId = Long.valueOf(claims.getSubject());
        Long espacoId = claims.get("espacoId", Long.class);
        String email = claims.get("email", String.class);
        boolean precisaTrocarSenha = Boolean.TRUE.equals(claims.get("precisaTrocarSenha", Boolean.class));
        return new UsuarioAutenticado(usuarioId, espacoId, email, precisaTrocarSenha);
    }
}
