package com.financeiro.dto;

import com.financeiro.entity.enums.StatusTransacao;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class FaturaDTO {
    private Long id;
    private Long cartaoId;
    private CartaoDTO cartao;
    private LocalDate dataFechamento;
    private LocalDate dataVencimento;
    private Long transacaoDespesaId;
    private BigDecimal valor;
    private StatusTransacao status;
    // Só preenchido na consulta de detalhe de uma fatura específica.
    private List<ItemFaturaDTO> itens;
}
