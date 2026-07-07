package com.financeiro.dto;

import com.financeiro.entity.enums.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoriaDTO {
    private Long id;

    @NotBlank
    private String nome;

    @NotNull
    private TipoTransacao tipo;

    @NotBlank
    private String cor;

    @NotBlank
    private String icone;
}
