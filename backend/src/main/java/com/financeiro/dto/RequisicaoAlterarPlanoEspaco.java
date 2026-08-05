package com.financeiro.dto;

import com.financeiro.entity.enums.CodigoPlano;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RequisicaoAlterarPlanoEspaco {
    @NotNull
    private CodigoPlano plano;
}
