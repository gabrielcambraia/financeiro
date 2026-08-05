package com.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequisicaoConfirmarCodigo {
    @NotBlank
    @Size(min = 6, max = 6, message = "O código deve ter 6 dígitos")
    private String codigo;
}
