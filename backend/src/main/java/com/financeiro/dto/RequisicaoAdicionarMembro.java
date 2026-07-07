package com.financeiro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RequisicaoAdicionarMembro {

    @NotBlank
    private String nome;

    @NotBlank
    @Email
    private String email;
}
