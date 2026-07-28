package com.financeiro.dto;

import com.financeiro.entity.enums.ModuloEspaco;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class RequisicaoAlterarModulosEspaco {
    @NotNull
    private Set<ModuloEspaco> modulos;
}
