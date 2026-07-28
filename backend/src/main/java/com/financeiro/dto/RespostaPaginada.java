package com.financeiro.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envelope de resposta paginada, em vez de serializar {@link Page} do Spring
 * Data cru (JSON verboso e instável entre versões). {@code pagina} é
 * 0-based, igual à convenção do Spring Data.
 */
public record RespostaPaginada<T>(
        List<T> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas
) {
    public static <T> RespostaPaginada<T> de(Page<?> pagina, List<T> itens) {
        return new RespostaPaginada<>(itens, pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
