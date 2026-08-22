package com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CentroCustoDTO {
    private Long id;

    @NotBlank
    private String nome;

    @NotBlank
    private String cor;

    private Long filialId;
}
