package com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BancoDTO {
    private Long id;

    @NotBlank
    private String nome;

    @NotBlank
    private String corPrimaria;

    @NotBlank
    private String sigla;

    // campo de resposta
    private boolean temLogo;
}
