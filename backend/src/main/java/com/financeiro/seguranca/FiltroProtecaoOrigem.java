package com.financeiro.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * Defesa em profundidade contra CSRF nas duas únicas rotas que dependem só do
 * cookie httpOnly de refresh ({@code /api/auth/renovar} e {@code /api/auth/sair}) —
 * o resto da API exige o header {@code Authorization: Bearer}, que um site
 * terceiro não consegue anexar numa requisição cross-site, então é imune a
 * CSRF por natureza (por isso {@code csrf().disable()} em
 * {@link com.financeiro.config.ConfiguracaoSeguranca}). O cookie de refresh já
 * é {@code SameSite=Lax}, o que barra a maioria dos navegadores, mas aqui
 * validamos explicitamente {@code Origin} (ou {@code Referer} como fallback)
 * contra a lista de origens confiáveis como segunda camada.
 */
@Component
public class FiltroProtecaoOrigem extends OncePerRequestFilter {

    private static final Set<String> ROTAS_PROTEGIDAS = Set.of("/api/auth/renovar", "/api/auth/sair");

    private final Set<String> origensPermitidas;

    public FiltroProtecaoOrigem(@Value("${financeiro.cors.origens-permitidas}") String[] origensPermitidas) {
        this.origensPermitidas = Set.of(origensPermitidas);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (ROTAS_PROTEGIDAS.contains(request.getRequestURI()) && !origemConfiavel(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"mensagem\":\"Origem não permitida.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean origemConfiavel(HttpServletRequest request) {
        String origem = request.getHeader("Origin");
        if (origem == null) {
            origem = origemDoReferer(request.getHeader("Referer"));
        }
        return origem != null && origensPermitidas.contains(origem);
    }

    private String origemDoReferer(String referer) {
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException ignorada) {
            return null;
        }
    }
}
