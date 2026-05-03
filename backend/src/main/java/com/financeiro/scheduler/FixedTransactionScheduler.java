package com.financeiro.scheduler;

import com.financeiro.entity.Transaction;
import com.financeiro.entity.enums.TransactionType;
import com.financeiro.repository.TransactionRepository;
import com.financeiro.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Scheduled(cron = "0 5 0 1 * *")
    @Transactional
    public void replicateFixedTransactions() {
        LocalDate today = LocalDate.now();
        YearMonth prevMonth = YearMonth.from(today.minusMonths(1));
        YearMonth currMonth = YearMonth.from(today);

        List<Transaction> fixed = repository.findByFixedTrueAndDateBetween(
                prevMonth.atDay(1).toString(), prevMonth.atEndOfMonth().toString());

        List<Transaction> existing = repository.findByDateBetweenOrderByDateDesc(
                currMonth.atDay(1).toString(), currMonth.atEndOfMonth().toString());

        log.info("Replicating {} fixed transactions from {} to {}", fixed.size(), prevMonth, currMonth);

        for (Transaction original : fixed) {
            boolean alreadyExists = existing.stream().anyMatch(t -> t.isFixed()
                    && t.getAccount().getId().equals(original.getAccount().getId())
                    && t.getAmount().compareTo(original.getAmount()) == 0
                    && t.getType() == original.getType());

            if (!alreadyExists) {
                Transaction copy = Transaction.builder()
                        .account(original.getAccount())
                        .category(original.getCategory())
                        .type(original.getType())
                        .paymentType(original.getPaymentType())
                        .amount(original.getAmount())
                        .description(original.getDescription())
                        .date(LocalDate.parse(original.getDate()).plusMonths(1).toString())
                        .fixed(true)
                        .build();
                repository.save(copy);
                accountService.adjustBalance(original.getAccount(),
                        original.getType() == TransactionType.INCOME
                                ? original.getAmount() : original.getAmount().negate());
            }
        }
    }
}
