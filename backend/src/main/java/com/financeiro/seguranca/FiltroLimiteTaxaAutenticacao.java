package com.financeiro.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Contém força bruta/credential stuffing em rotas públicas de autenticação:
 * limita tentativas por IP (via {@link LimitadorTaxa}) antes de chegar ao
 * controller. Aplica-se só a rotas sem exigência de JWT — as demais já
 * exigem sessão autenticada, o que por si só encarece um ataque automatizado.
 */
@Component
public class FiltroLimiteTaxaAutenticacao extends OncePerRequestFilter {

    private static final Set<String> ROTAS_LIMITADAS = Set.of(
            "/api/auth/login", "/api/auth/register");

    private final LimitadorTaxa limitadorTaxa;

    public FiltroLimiteTaxaAutenticacao(LimitadorTaxa limitadorTaxa) {
        this.limitadorTaxa = limitadorTaxa;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (ROTAS_LIMITADAS.contains(request.getRequestURI())) {
            String chave = ipCliente(request) + ":" + request.getRequestURI();
            if (!limitadorTaxa.permitir(chave)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"mensagem\":\"Muitas tentativas. Aguarde um instante e tente novamente.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String ipCliente(HttpServletRequest request) {
        String encaminhadoPor = request.getHeader("X-Forwarded-For");
        if (encaminhadoPor != null && !encaminhadoPor.isBlank()) {
            return encaminhadoPor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
