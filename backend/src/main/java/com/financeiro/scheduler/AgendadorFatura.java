package com.financeiro.scheduler;

import com.financeiro.entity.Cartao;
import com.financeiro.entity.Fatura;
import com.financeiro.entity.ItemFatura;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.FaturaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * No dia do fechamento de cada cartão, soma os {@link ItemFatura} ainda
 * soltos (não cancelados) e gera UMA despesa (débito) na conta de pagamento
 * do cartão — essa despesa nasce PENDENTE e segue o ciclo de vida manual de
 * TransacaoService (pagar/estornar/cancelar). Não há ajuste de saldo aqui:
 * compras no cartão nunca tocam saldo de conta, só a fatura fechada toca.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgendadorFatura {

    private final CartaoRepository cartaoRepository;
    private final ItemFaturaRepository itemFaturaRepository;
    private final FaturaRepository faturaRepository;
    private final TransacaoRepository transacaoRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        log.info("Verificando fechamento de faturas na inicialização...");
        process();
    }

    @Scheduled(cron = "0 10 0 * * *")
    @Transactional
    public void onDailyCheck() {
        log.info("Verificando fechamento de faturas (checagem diária)...");
        process();
    }

    private void process() {
        try {
            MDC.put("idRequisicao", "agendador-fatura-" + UUID.randomUUID());
            LocalDate hoje = LocalDate.now();

            for (Cartao cartao : cartaoRepository.findAll()) {
                LocalDate dataFechamento = ajustarDiaParaMes(cartao.getDiaFechamento(), YearMonth.from(hoje));

                // >= em vez de == : se o app ficou dias desligado e passou do dia
                // de fechamento, fecha assim que religar (mesmo raciocínio do
                // AgendadorTransacaoFixa). A checagem de existência abaixo garante
                // que cada ciclo só fecha uma vez.
                if (hoje.isBefore(dataFechamento)) {
                    continue;
                }
                if (faturaRepository.existsByCartaoIdAndDataFechamento(cartao.getId(), dataFechamento)) {
                    continue;
                }

                fecharFatura(cartao, dataFechamento);
            }
        } finally {
            MDC.clear();
        }
    }

    private void fecharFatura(Cartao cartao, LocalDate dataFechamento) {
        List<ItemFatura> itensAbertos = itemFaturaRepository
                .findByCartaoIdAndFaturaIdIsNullAndDataCancelamentoIsNull(cartao.getId());
        if (itensAbertos.isEmpty()) {
            log.info("Cartão {} sem itens em aberto no fechamento de {} — nada a fazer", cartao.getId(), dataFechamento);
            return;
        }

        BigDecimal total = itensAbertos.stream()
                .map(ItemFatura::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate dataVencimento = calcularVencimento(cartao, dataFechamento);

        Transacao despesa = Transacao.builder()
                .conta(cartao.getContaPagamento())
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(total)
                .descricao("Fatura " + cartao.getNome() + " " + String.format("%02d/%d",
                        dataVencimento.getMonthValue(), dataVencimento.getYear()))
                .data(dataFechamento)
                .dataVencimento(dataVencimento)
                .saldoAjustado(false)
                .fixa(false)
                .espacoId(cartao.getEspacoId())
                .usuarioId(itensAbertos.get(0).getUsuarioId())
                .build();
        transacaoRepository.save(despesa);

        Fatura fatura = Fatura.builder()
                .cartao(cartao)
                .dataFechamento(dataFechamento)
                .dataVencimento(dataVencimento)
                .transacaoDespesa(despesa)
                .espacoId(cartao.getEspacoId())
                .build();
        faturaRepository.save(fatura);

        itensAbertos.forEach(item -> item.setFatura(fatura));
        itemFaturaRepository.saveAll(itensAbertos);

        log.info("Fatura fechada: cartao={} total={} vencimento={} itens={}",
                cartao.getId(), total, dataVencimento, itensAbertos.size());
    }

    private LocalDate calcularVencimento(Cartao cartao, LocalDate dataFechamento) {
        YearMonth mesFechamento = YearMonth.from(dataFechamento);
        // Se o dia de vencimento configurado é menor que o dia de fechamento,
        // o vencimento cai no mês seguinte (padrão de qualquer cartão real:
        // fecha dia 25, vence dia 05 do mês seguinte).
        YearMonth mesVencimento = cartao.getDiaVencimento() < cartao.getDiaFechamento()
                ? mesFechamento.plusMonths(1)
                : mesFechamento;
        return ajustarDiaParaMes(cartao.getDiaVencimento(), mesVencimento);
    }

    private LocalDate ajustarDiaParaMes(int dia, YearMonth mes) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }
}
