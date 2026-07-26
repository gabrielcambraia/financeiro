package com.financeiro.dto;

import com.financeiro.entity.enums.TipoAtivo;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PatrimonioDTO {
    private BigDecimal totalPatrimonio;
    private List<ResumoTipo> porTipo;
    private List<EvolucaoMensal> evolucaoMensal;

    @Data
    @Builder
    public static class ResumoTipo {
        private TipoAtivo tipo;
        private BigDecimal valor;
    }

    @Data
    @Builder
    public static class EvolucaoMensal {
        private String mes;
        private BigDecimal valor;
    }
}
