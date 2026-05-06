package com.financeiro.repository;

import com.financeiro.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByDateBetweenOrderByDateDesc(String start, String end);

    List<Transaction> findByAccountIdAndDateBetweenOrderByDateDesc(Long accountId, String start, String end);

    List<Transaction> findByFixedTrueAndDateBetween(String start, String end);

    List<Transaction> findByInstallmentGroupId(String groupId);

    List<Transaction> findByInstallmentGroupIdAndDateGreaterThanEqual(String groupId, String fromDate);

    List<Transaction> findByFixedTrueAndDateGreaterThanEqual(String fromDate);

    List<Transaction> findByFixedTrueAndBalanceAdjustedFalseAndDateBetween(String start, String end);

    List<Transaction> findByBalanceAdjustedFalseAndDateLessThanEqual(String date);

    boolean existsByFixedTrueAndAccountIdAndAmountAndTypeAndDescriptionAndDateBetween(
            Long accountId, java.math.BigDecimal amount,
            com.financeiro.entity.enums.TransactionType type,
            String description, String start, String end);

    List<Transaction> findByDateBetweenOrderByDateAsc(String start, String end);

    List<Transaction> findByAccountIdAndDateBetweenOrderByDateAsc(Long accountId, String start, String end);
}
