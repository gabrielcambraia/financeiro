package com.financeiro.dto;

import com.financeiro.entity.enums.PaymentType;
import com.financeiro.entity.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionDTO {
    private Long id;

    @NotNull
    private Long accountId;

    private Long categoryId;

    @NotNull
    private TransactionType type;

    @NotNull
    private PaymentType paymentType;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String description;

    @NotNull
    private String date;

    private boolean fixed;

    private Integer installmentTotal;

    // response fields
    private AccountDTO account;
    private CategoryDTO category;
    private Integer installmentNumber;
    private String installmentGroupId;
}
