package com.financeiro.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lê o header {@code Authorization: Bearer <token>}, valida via
 * {@link ServicoJwt} e popula o {@link SecurityContextHolder} com o
 * {@link UsuarioAutenticado} como principal. Se o token estiver ausente ou
 * inválido, apenas segue a cadeia sem autenticar — quem devolve 401 é o
 * entry point configurado em {@code ConfiguracaoSeguranca}.
 */
@Component
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

    private final ServicoJwt servicoJwt;

    public FiltroAutenticacaoJwt(ServicoJwt servicoJwt) {
        this.servicoJwt = servicoJwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                UsuarioAutenticado usuario = servicoJwt.validarEExtrair(header.substring(7));
                var autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(autenticacao);
            } catch (Exception ignorada) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
