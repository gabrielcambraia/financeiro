package com.financeiro.seguranca;

import com.financeiro.entity.enums.NivelAcesso;
import com.financeiro.entity.enums.PapelUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Gera e valida os tokens JWT. O token carrega {@code usuarioId} e
 * {@code espacoId} — é a partir dessas claims que
 * {@link com.financeiro.context.ContextoEspacoSeguranca} resolve o tenant
 * ativo da requisição. Também carrega {@code papel} e {@code nivelAcesso},
 * usados pelo frontend para adaptar a UI — a autorização de fato (ex.:
 * {@code AutorizacaoEspaco.exigirDono}) sempre revalida o papel no banco,
 * já que o claim pode estar desatualizado enquanto o token não expira.
 * {@code precisaTrocarSenha}/{@code precisaCadastrarTelefone} são gates de
 * primeiro acesso, aplicados por {@code FiltroTrocaSenhaObrigatoria}/
 * {@code FiltroCadastroTelefoneObrigatorio}.
 */
@Service
public class ServicoJwt {

    private final SecretKey chave;
    private final long validadeMinutos;

    public ServicoJwt(
            @Value("${financeiro.jwt.segredo}") String segredo,
            @Value("${financeiro.jwt.validade-acesso-minutos:5}") long validadeMinutos) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validadeMinutos = validadeMinutos;
    }

    public String gerarToken(Long usuarioId, Long espacoId, String email, boolean precisaTrocarSenha,
                              boolean precisaCadastrarTelefone, NivelAcesso nivelAcesso, PapelUsuario papel) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("espacoId", espacoId)
                .claim("email", email)
                .claim("precisaTrocarSenha", precisaTrocarSenha)
                .claim("precisaCadastrarTelefone", precisaCadastrarTelefone)
                .claim("nivelAcesso", nivelAcesso.name())
                .claim("papel", papel.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusSeconds(validadeMinutos * 60)))
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
        boolean precisaCadastrarTelefone = Boolean.TRUE.equals(claims.get("precisaCadastrarTelefone", Boolean.class));
        NivelAcesso nivelAcesso = NivelAcesso.valueOf(
                claims.getOrDefault("nivelAcesso", NivelAcesso.USUARIO.name()).toString());
        PapelUsuario papel = PapelUsuario.valueOf(
                claims.getOrDefault("papel", PapelUsuario.MEMBRO.name()).toString());
        return new UsuarioAutenticado(usuarioId, espacoId, email, precisaTrocarSenha, precisaCadastrarTelefone,
                nivelAcesso, papel);
    }
}
