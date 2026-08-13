package com.financeiro.dto;

import com.financeiro.entity.enums.EscopoAtualizacao;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversaoParaCartaoDTO {
    @NotNull
    private Long cartaoId;
    private Integer totalParcelas;
    private EscopoAtualizacao escopo;
}
