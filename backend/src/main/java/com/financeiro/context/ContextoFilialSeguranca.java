package com.financeiro.context;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Lê o {@code filialId} populado pelo {@code FiltroFilialAtual} via
 * {@link RequestContextHolder}. Retorna {@code null} quando o header
 * {@code X-Filial-Id} está ausente, indicando "Todas as filiais".
 */
@Component
public class ContextoFilialSeguranca implements ContextoFilial {

    public static final String ATRIBUTO = "filialAtualId";

    @Override
    public Long filialAtual() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        return (Long) sra.getAttribute(ATRIBUTO, ServletRequestAttributes.SCOPE_REQUEST);
    }
}
