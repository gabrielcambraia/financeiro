package com.financeiro.seguranca;

import com.financeiro.context.ContextoEntidadeSeguranca;
import com.financeiro.repository.EntidadeRepository;
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
 * Lê o header {@code X-Entidade-Id} e popula o {@link ContextoEntidadeSeguranca}.
 * <p>
 * Ausente/vazio → nenhum filtro (= "Todas as entidades").
 * Presente e válido → valida que a entidade pertence ao espaço do usuário autenticado; 403 se não.
 * Presente e não-numérico → 400.
 * Rotas {@code /api/entidades/**} são ignoradas (CRUD de entidades não é filtrado por entidade).
 */
@Component
public class FiltroEntidadeAtual extends OncePerRequestFilter {

    private final EntidadeRepository entidadeRepository;

    public FiltroEntidadeAtual(EntidadeRepository entidadeRepository) {
        this.entidadeRepository = entidadeRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String headerValor = request.getHeader("X-Entidade-Id");

        if (headerValor != null && !headerValor.isBlank()
                && !request.getRequestURI().startsWith("/api/entidades")) {
            long entidadeId;
            try {
                entidadeId = Long.parseLong(headerValor.trim());
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Entidade-Id inválido");
                return;
            }

            var autenticacao = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();

            if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
                var entidade = entidadeRepository.findById(entidadeId).orElse(null);
                if (entidade == null || !entidade.getEspacoId().equals(usuario.espacoId())) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Entidade não pertence ao espaço atual");
                    return;
                }

                var attrs = RequestContextHolder.getRequestAttributes();
                if (attrs instanceof ServletRequestAttributes sra) {
                    sra.setAttribute(ContextoEntidadeSeguranca.ATRIBUTO, entidadeId,
                            ServletRequestAttributes.SCOPE_REQUEST);
                }
                MDC.put("entidadeId", String.valueOf(entidadeId));
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Autenticação necessária");
                return;
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("entidadeId");
        }
    }
}
