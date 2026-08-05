package com.financeiro.dto;

import java.math.BigDecimal;
import java.util.List;

public record RespostaImpacto(
        boolean bloqueado,
        String motivoBloqueio,
        List<ItemImpacto> itensAfetados,
        OrigemVinculada origem
) {
    public record ItemImpacto(String tipo, String descricao, BigDecimal valor) {}
    public record OrigemVinculada(String tipo, Long id, String nome, String efeito) {}
}
