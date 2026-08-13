package com.financeiro.dto;

import com.financeiro.entity.enums.EscopoAtualizacao;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ConversaoParaDebitoDTO {
    @NotNull
    private Long contaId;
    private EscopoAtualizacao escopo;
    private Boolean quitarNaCriacao;
    private LocalDate dataPagamento;
}
