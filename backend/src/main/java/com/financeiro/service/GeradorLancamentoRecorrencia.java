package com.financeiro.service;

import com.financeiro.entity.Cartao;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Fatura;
import com.financeiro.entity.ItemFatura;
import com.financeiro.entity.Recorrencia;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeradorLancamentoRecorrencia {

    private final TransacaoRepository transacaoRepository;
    private final ItemFaturaRepository itemFaturaRepository;
    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaService contaService;
    private final ResolvedorFaturaAlvo resolvedorFaturaAlvo;
    private final ConversaoLancamentoService conversaoLancamentoService;

    /**
     * Gera o lançamento do mês alvo para a recorrência informada.
     * Idempotente: se já existir um lançamento no mês, retorna sem criar outro
     * (garantido também pelo UNIQUE index na migration).
     */
    @Transactional
    public void gerarParaMes(Recorrencia r, YearMonth mes) {
        LocalDate inicioMes = mes.atDay(1);
        LocalDate fimMes = mes.atEndOfMonth();

        if (r.getTipoPagamento() == TipoPagamento.DEBITO) {
            if (transacaoRepository.existsByOrigemRecorrenciaIdAndDataBetween(r.getId(), inicioMes, fimMes)) {
                log.debug("Recorrência {} já gerou transação em {}", r.getId(), mes);
                return;
            }
            gerarTransacao(r, mes);
        } else {
            if (itemFaturaRepository.existsByOrigemRecorrenciaIdAndDataBetween(r.getId(), inicioMes, fimMes)) {
                log.debug("Recorrência {} já gerou item de fatura em {}", r.getId(), mes);
                return;
            }
            gerarItemFatura(r, mes);
        }
    }

    private void gerarTransacao(Recorrencia r, YearMonth mes) {
        Conta conta = contaRepository.findByIdAndEspacoId(r.getContaId(), r.getEspacoId()).orElse(null);
        if (conta == null) {
            log.warn("Recorrência {}: conta {} não encontrada, pulando geração em {}", r.getId(), r.getContaId(), mes);
            return;
        }

        Categoria categoria = r.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(r.getCategoriaId(), r.getEspacoId()).orElse(null) : null;

        int dia = Math.min(r.getDiaCompetencia(), mes.lengthOfMonth());
        LocalDate data = mes.atDay(dia);

        int diaVenc = r.getDiaVencimento() != null ? r.getDiaVencimento() : r.getDiaCompetencia();
        LocalDate dataVencimento = mes.atDay(Math.min(diaVenc, mes.lengthOfMonth()));

        Transacao t = Transacao.builder()
                .conta(conta)
                .categoria(categoria)
                .tipo(r.getTipo())
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(r.getValor())
                .descricao(r.getDescricao())
                .data(data)
                .dataVencimento(dataVencimento)
                .fixa(false)
                .debitoAutomatico(r.isDebitoAutomatico())
                .saldoAjustado(false)
                .espacoId(r.getEspacoId())
                .usuarioId(r.getUsuarioId())
                .centroCustoId(r.getCentroCustoId())
                .origemRecorrenciaId(r.getId())
                .build();

        transacaoRepository.save(t);
        log.info("Recorrência {}: transação gerada para {} (conta={})", r.getId(), mes, conta.getId());
    }

    private void gerarItemFatura(Recorrencia r, YearMonth mes) {
        Cartao cartao = cartaoRepository.findByIdAndEspacoId(r.getCartaoId(), r.getEspacoId()).orElse(null);
        if (cartao == null) {
            log.warn("Recorrência {}: cartão {} não encontrado, pulando geração em {}", r.getId(), r.getCartaoId(), mes);
            return;
        }

        Categoria categoria = r.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(r.getCategoriaId(), r.getEspacoId()).orElse(null) : null;

        int dia = Math.min(r.getDiaCompetencia(), mes.lengthOfMonth());
        LocalDate data = mes.atDay(dia);

        // Resolve a fatura que abrange essa data (pode ser nula se ainda não fechou)
        Fatura fatura = resolvedorFaturaAlvo.resolver(cartao, data);

        ItemFatura item = ItemFatura.builder()
                .cartao(cartao)
                .categoria(categoria)
                .valor(r.getValor())
                .descricao(r.getDescricao())
                .data(data)
                .fatura(fatura)
                .fixa(false)
                .serieAtiva(false)
                .espacoId(r.getEspacoId())
                .usuarioId(r.getUsuarioId())
                .centroCustoId(r.getCentroCustoId())
                .origemRecorrenciaId(r.getId())
                .build();

        itemFaturaRepository.save(item);

        // Se caiu em fatura já fechada, reconcilia a despesa consolidada (reverte e
        // reaplica o saldo se a fatura já estava paga) em vez de somar o valor direto.
        if (fatura != null) {
            conversaoLancamentoService.reconciliarFaturaFechadaAdicao(fatura, r.getValor());
        }

        log.info("Recorrência {}: item de fatura gerado para {} (cartão={})", r.getId(), mes, cartao.getId());
    }
}
