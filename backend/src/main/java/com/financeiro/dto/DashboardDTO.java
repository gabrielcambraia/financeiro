package com.financeiro.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance;
    private FlowSummary realized;
    private FlowSummary pending;
    private List<CategorySummary> expensesByCategory;
    private List<CategorySummary> incomesByCategory;
    private List<MonthlyTrend> monthlyTrend;
    private List<AccountBalance> accountBalances;
    private List<DailyBalance> dailyBalance;

    @Data
    @Builder
    public static class FlowSummary {
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal balance;
    }

    @Data
    @Builder
    public static class CategorySummary {
        private CategoryDTO category;
        private BigDecimal total;
        private double percentage;
    }

    @Data
    @Builder
    public static class MonthlyTrend {
        private String month;
        private BigDecimal income;
        private BigDecimal expense;
    }

    @Data
    @Builder
    public static class AccountBalance {
        private AccountDTO account;
        private BigDecimal balance;
    }

    @Data
    @Builder
    public static class DailyBalance {
        private String date;
        private BigDecimal balance;
    }
}
