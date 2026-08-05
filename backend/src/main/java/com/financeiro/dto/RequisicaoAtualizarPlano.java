package com.financeiro.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RequisicaoAtualizarPlano {
    @NotNull
    @Min(value = 1, message = "O limite deve ser pelo menos 1")
    private Integer limiteEntidades;

    private Boolean ativo;
}
