package com.financeiro.seguranca;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Escreve/lê o cookie httpOnly que carrega o refresh token. É restrito a
 * {@code /api/auth} (nunca é enviado nas chamadas normais da API) e marcado
 * {@code Secure} em produção — em dev local (proxy Vite em http) isso é
 * desligado via {@code financeiro.cookie.seguro=false}.
 */
@Component
public class AuxiliarCookieRenovacao {

    public static final String NOME_COOKIE = "financeiro.refresh";

    private final boolean cookieSeguro;
    private final ServicoTokenAtualizacao servicoTokenAtualizacao;

    public AuxiliarCookieRenovacao(
            @Value("${financeiro.cookie.seguro:true}") boolean cookieSeguro,
            ServicoTokenAtualizacao servicoTokenAtualizacao) {
        this.cookieSeguro = cookieSeguro;
        this.servicoTokenAtualizacao = servicoTokenAtualizacao;
    }

    public void escrever(HttpServletResponse response, String tokenBruto) {
        ResponseCookie cookie = ResponseCookie.from(NOME_COOKIE, tokenBruto)
                .httpOnly(true)
                .secure(cookieSeguro)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(servicoTokenAtualizacao.validadeSegundos()))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void limpar(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(NOME_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSeguro)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public String ler(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (NOME_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
