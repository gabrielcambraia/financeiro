package com.financeiro.seguranca;

import com.financeiro.context.ContextoFilialSeguranca;
import com.financeiro.repository.FilialRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lê o header {@code X-Filial-Id} e popula o {@link ContextoFilialSeguranca}.
 * <p>
 * Ausente/vazio → nenhum filtro (= "Todas as filiais").
 * Presente e válido → valida que a filial pertence ao espaço do usuário autenticado; 403 se não.
 * Presente e não-numérico → 400.
 * Rotas {@code /api/filiais/**} são ignoradas (CRUD de filiais não é filtrado por filial).
 */
@Component
public class FiltroFilialAtual extends OncePerRequestFilter {

    private final FilialRepository filialRepository;

    public FiltroFilialAtual(FilialRepository filialRepository) {
        this.filialRepository = filialRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String headerValor = request.getHeader("X-Filial-Id");

        if (headerValor != null && !headerValor.isBlank()
                && !request.getRequestURI().startsWith("/api/filiais")) {
            long filialId;
            try {
                filialId = Long.parseLong(headerValor.trim());
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Filial-Id inválido");
                return;
            }

            var autenticacao = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();

            if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
                var filial = filialRepository.findById(filialId).orElse(null);
                if (filial == null || !filial.getEspacoId().equals(usuario.espacoId())) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Filial não pertence ao espaço atual");
                    return;
                }

                var attrs = RequestContextHolder.getRequestAttributes();
                if (attrs instanceof ServletRequestAttributes sra) {
                    sra.setAttribute(ContextoFilialSeguranca.ATRIBUTO, filialId,
                            ServletRequestAttributes.SCOPE_REQUEST);
                }
                MDC.put("filialId", String.valueOf(filialId));
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Autenticação necessária");
                return;
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("filialId");
        }
    }
}
