package com.financeiro.scheduler;

import com.financeiro.entity.Transaction;
import com.financeiro.entity.enums.TransactionType;
import com.financeiro.repository.TransactionRepository;
import com.financeiro.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FixedTransactionScheduler {

    private final TransactionRepository repository;
    private final AccountService accountService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        log.info("Verificando transações fixas na inicialização...");
        process();
    }

    @Scheduled(cron = "0 5 0 1 * *")
    @Transactional
    public void onFirstOfMonth() {
        log.info("Processando transações fixas (dia 1° do mês)...");
        process();
    }

    private void process() {
        YearMonth currMonth = YearMonth.from(LocalDate.now());

        // 1. Ajusta saldo de qualquer transação (fixa ou não) cuja data já chegou
        List<Transaction> due = repository.findByBalanceAdjustedFalseAndDateLessThanEqual(
                LocalDate.now().toString());

        for (Transaction t : due) {
            accountService.adjustBalance(t.getAccount(),
                    t.getType() == TransactionType.INCOME ? t.getAmount() : t.getAmount().negate());
            t.setBalanceAdjusted(true);
            repository.save(t);
            log.info("Saldo ajustado: id={} data={}", t.getId(), t.getDate());
        }

        // 2. Garante janela de 12 meses à frente para cada transação fixa ativa
        for (int offset = 1; offset <= 12; offset++) {
            YearMonth targetMonth = currMonth.plusMonths(offset);
            extendTo(currMonth, targetMonth);
        }
    }

    private void extendTo(YearMonth sourceMonth, YearMonth targetMonth) {
        List<Transaction> templates = repository.findByFixedTrueAndDateBetween(
                sourceMonth.atDay(1).toString(), sourceMonth.atEndOfMonth().toString());

        for (Transaction original : templates) {
            boolean exists = repository.existsByFixedTrueAndAccountIdAndAmountAndTypeAndDescriptionAndDateBetween(
                    original.getAccount().getId(),
                    original.getAmount(),
                    original.getType(),
                    original.getDescription(),
                    targetMonth.atDay(1).toString(),
                    targetMonth.atEndOfMonth().toString());

            if (!exists) {
                int day = Math.min(LocalDate.parse(original.getDate()).getDayOfMonth(), targetMonth.lengthOfMonth());
                Transaction copy = Transaction.builder()
                        .account(original.getAccount())
                        .category(original.getCategory())
                        .type(original.getType())
                        .paymentType(original.getPaymentType())
                        .amount(original.getAmount())
                        .description(original.getDescription())
                        .date(targetMonth.atDay(day).toString())
                        .fixed(true)
                        .balanceAdjusted(false)
                        .build();
                repository.save(copy);
                log.info("Entrada criada para {} (conta={})", targetMonth, original.getAccount().getId());
            }
        }
    }
}
