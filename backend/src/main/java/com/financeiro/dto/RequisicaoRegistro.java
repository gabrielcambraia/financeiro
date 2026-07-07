package com.financeiro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequisicaoRegistro {

    @NotBlank
    private String nome;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres")
    private String senha;
}
