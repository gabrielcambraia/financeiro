package com.financeiro.context;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Lê o {@code entidadeId} populado pelo {@code FiltroEntidadeAtual} via
 * {@link RequestContextHolder}. Retorna {@code null} quando o header
 * {@code X-Entidade-Id} está ausente, indicando "Todas as entidades".
 */
@Component
public class ContextoEntidadeSeguranca implements ContextoEntidade {

    static final String ATRIBUTO = "entidadeAtualId";

    @Override
    public Long entidadeAtual() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        return (Long) sra.getAttribute(ATRIBUTO, ServletRequestAttributes.SCOPE_REQUEST);
    }
}
