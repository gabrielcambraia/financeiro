package com.financeiro.service;

import com.financeiro.dto.CategoryDTO;
import com.financeiro.dto.DashboardDTO;
import com.financeiro.entity.Transaction;
import com.financeiro.entity.enums.TransactionType;
import com.financeiro.repository.AccountRepository;
import com.financeiro.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    public DashboardDTO getDashboard(String month, Long accountId) {
        YearMonth ym = YearMonth.parse(month);
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();

        List<Transaction> monthTx = fetch(accountId, start, end, false);

        BigDecimal totalIncome = sum(monthTx, TransactionType.INCOME);
        BigDecimal totalExpense = sum(monthTx, TransactionType.EXPENSE);

        return DashboardDTO.builder()
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netBalance(totalIncome.subtract(totalExpense))
                .expensesByCategory(buildCategorySummary(monthTx, TransactionType.EXPENSE, totalExpense))
                .incomesByCategory(buildCategorySummary(monthTx, TransactionType.INCOME, totalIncome))
                .monthlyTrend(buildMonthlyTrend(ym, accountId))
                .accountBalances(buildAccountBalances())
                .dailyBalance(buildDailyBalance(monthTx, ym))
                .build();
    }

    private List<Transaction> fetch(Long accountId, String start, String end, boolean asc) {
        if (accountId != null) {
            return asc
                    ? transactionRepository.findByAccountIdAndDateBetweenOrderByDateAsc(accountId, start, end)
                    : transactionRepository.findByAccountIdAndDateBetweenOrderByDateDesc(accountId, start, end);
        }
        return asc
                ? transactionRepository.findByDateBetweenOrderByDateAsc(start, end)
                : transactionRepository.findByDateBetweenOrderByDateDesc(start, end);
    }

    private BigDecimal sum(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<DashboardDTO.CategorySummary> buildCategorySummary(
            List<Transaction> transactions, TransactionType type, BigDecimal total) {
        Map<Long, List<Transaction>> grouped = transactions.stream()
                .filter(t -> t.getType() == type && t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

        return grouped.entrySet().stream().map(entry -> {
            Transaction sample = entry.getValue().get(0);
            BigDecimal catTotal = entry.getValue().stream()
                    .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            double pct = total.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : catTotal.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100;

            CategoryDTO catDTO = new CategoryDTO();
            catDTO.setId(sample.getCategory().getId());
            catDTO.setName(sample.getCategory().getName());
            catDTO.setType(sample.getCategory().getType());
            catDTO.setColor(sample.getCategory().getColor());
            catDTO.setIcon(sample.getCategory().getIcon());

            return DashboardDTO.CategorySummary.builder()
                    .category(catDTO).total(catTotal).percentage(pct).build();
        }).sorted(Comparator.comparing(DashboardDTO.CategorySummary::getTotal).reversed()).toList();
    }

    private List<DashboardDTO.MonthlyTrend> buildMonthlyTrend(YearMonth current, Long accountId) {
        String startDate = current.minusMonths(5).atDay(1).toString();
        String endDate = current.atEndOfMonth().toString();

        List<Transaction> all = fetch(accountId, startDate, endDate, true);

        List<DashboardDTO.MonthlyTrend> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = current.minusMonths(i);
            String ms = m.atDay(1).toString();
            String me = m.atEndOfMonth().toString();
            List<Transaction> monthTx = all.stream()
                    .filter(t -> t.getDate().compareTo(ms) >= 0 && t.getDate().compareTo(me) <= 0)
                    .toList();
            trend.add(DashboardDTO.MonthlyTrend.builder()
                    .month(m.toString())
                    .income(sum(monthTx, TransactionType.INCOME))
                    .expense(sum(monthTx, TransactionType.EXPENSE))
                    .build());
        }
        return trend;
    }

    private List<DashboardDTO.AccountBalance> buildAccountBalances() {
        return accountRepository.findAll().stream().map(a ->
                DashboardDTO.AccountBalance.builder()
                        .account(accountService.toDTO(a))
                        .balance(a.getBalance())
                        .build()
        ).toList();
    }

    private List<DashboardDTO.DailyBalance> buildDailyBalance(List<Transaction> transactions, YearMonth ym) {
        List<DashboardDTO.DailyBalance> result = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            String dateStr = ym.atDay(day).toString();
            for (Transaction t : transactions) {
                if (dateStr.equals(t.getDate())) {
                    running = running.add(t.getType() == TransactionType.INCOME
                            ? t.getAmount() : t.getAmount().negate());
                }
            }
            result.add(DashboardDTO.DailyBalance.builder().date(dateStr).balance(running).build());
        }
        return result;
    }
}
