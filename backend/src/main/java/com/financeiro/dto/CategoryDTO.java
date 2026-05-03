package com.financeiro.dto;

import com.financeiro.entity.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CategoryDTO {
    private Long id;

    @NotBlank
    private String name;

    @NotNull
    private TransactionType type;

    @NotBlank
    private String color;

    @NotBlank
    private String icon;
}
