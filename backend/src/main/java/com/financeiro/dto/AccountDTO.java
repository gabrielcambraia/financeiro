package com.financeiro.dto;

import com.financeiro.entity.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountDTO {
    private Long id;

    @NotBlank
    private String name;

    @NotNull
    private AccountType type;

    @NotNull
    private BigDecimal balance;

    @NotBlank
    private String color;

    @NotBlank
    private String icon;
}
