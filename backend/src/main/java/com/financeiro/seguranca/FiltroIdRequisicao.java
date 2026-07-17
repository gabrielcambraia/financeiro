package com.financeiro.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gera (ou reaproveita) um id de correlação por requisição, disponível em
 * todo log via MDC e devolvido ao cliente no header de resposta — permite
 * cruzar um erro reportado pelo usuário com a linha exata do log no
 * servidor. Roda antes de qualquer outro filtro para que o id já exista
 * nos logs deles (ex.: rejeição por limite de taxa).
 */
@Component
public class FiltroIdRequisicao extends OncePerRequestFilter {

    public static final String CABECALHO_ID_REQUISICAO = "X-Request-Id";
    public static final String CHAVE_MDC_ID_REQUISICAO = "idRequisicao";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idRecebido = request.getHeader(CABECALHO_ID_REQUISICAO);
        String id = (idRecebido != null && !idRecebido.isBlank()) ? idRecebido : UUID.randomUUID().toString();

        try {
            MDC.put(CHAVE_MDC_ID_REQUISICAO, id);
            response.setHeader(CABECALHO_ID_REQUISICAO, id);
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
