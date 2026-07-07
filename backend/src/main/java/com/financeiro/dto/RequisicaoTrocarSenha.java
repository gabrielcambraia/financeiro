package com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequisicaoTrocarSenha {

    @NotBlank
    private String senhaAtual;

    @NotBlank
    @Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres")
    private String novaSenha;
}
