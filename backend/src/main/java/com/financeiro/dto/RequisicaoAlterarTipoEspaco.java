package com.financeiro.dto;

import com.financeiro.entity.enums.TipoEspaco;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RequisicaoAlterarTipoEspaco {
    @NotNull
    private TipoEspaco tipo;
}
