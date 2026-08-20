package com.financeiro.dto;

import com.financeiro.entity.enums.DirecaoTransferencia;
import com.financeiro.entity.enums.StatusTransacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransacaoDTO {
    private Long id;

    @NotNull
    private Long contaId;

    // Só usado na criação quando tipo == TRANSFERENCIA: conta que recebe o valor.
    private Long contaDestinoId;

    private Long categoriaId;

    @NotNull
    private TipoTransacao tipo;

    @NotNull
    private TipoPagamento tipoPagamento;

    @NotNull
    @Positive
    private BigDecimal valor;

    private String descricao;

    @NotNull
    private LocalDate data;

    private LocalDate dataVencimento;

    private LocalDate dataPagamento;

    private LocalDate dataCancelamento;

    // Controla, na criação, se a transação já nasce paga (default: true para
    // data <= hoje, false para datas futuras). Ver TransacaoService.create().
    private Boolean quitarNaCriacao;

    private boolean fixa;

    private boolean debitoAutomatico;

    private Integer totalParcelas;

    private Long centroCustoId;

    // campos de resposta
    private ContaDTO conta;
    private CategoriaDTO categoria;
    private Integer numeroParcela;
    private String grupoParcelaId;
    private Long usuarioId;
    private StatusTransacao status;
    // Conta do outro lado da transferência: destino quando esta linha é SAIDA,
    // origem quando é ENTRADA. Só preenchido para tipo == TRANSFERENCIA.
    private ContaDTO contaVinculada;
    private String transferenciaId;
    private DirecaoTransferencia direcaoTransferencia;
    private BigDecimal multa;

    // "META", "ATIVO" ou "DIVIDA" quando a transação é derivada de um desses
    // fluxos e não pode ser editada; null para transações comuns.
    private String origemDerivada;

    // ID da Recorrencia que gerou este lançamento; null se avulso.
    private Long origemRecorrenciaId;
}
