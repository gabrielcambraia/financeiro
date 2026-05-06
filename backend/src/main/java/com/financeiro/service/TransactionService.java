package com.financeiro.service;

import com.financeiro.dto.AccountDTO;
import com.financeiro.dto.CategoryDTO;
import com.financeiro.dto.TransactionDTO;
import com.financeiro.entity.Account;
import com.financeiro.entity.Category;
import com.financeiro.entity.Transaction;
import com.financeiro.entity.enums.TransactionType;
import com.financeiro.repository.AccountRepository;
import com.financeiro.repository.CategoryRepository;
import com.financeiro.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository repository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountService accountService;

    public List<TransactionDTO> findByFilters(String month, Long accountId, TransactionType type, Long categoryId) {
        YearMonth ym = YearMonth.parse(month);
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();
        List<Transaction> raw = accountId != null
                ? repository.findByAccountIdAndDateBetweenOrderByDateDesc(accountId, start, end)
                : repository.findByDateBetweenOrderByDateDesc(start, end);
        return raw.stream()
                .filter(t -> type == null || t.getType() == type)
                .filter(t -> categoryId == null
                        || (t.getCategory() != null && t.getCategory().getId().equals(categoryId)))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<TransactionDTO> create(TransactionDTO dto) {
        Account account = accountRepository.findById(dto.getAccountId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        Category category = dto.getCategoryId() != null
                ? categoryRepository.findById(dto.getCategoryId()).orElse(null)
                : null;

        List<Transaction> created = new ArrayList<>();

        if (dto.getInstallmentTotal() != null && dto.getInstallmentTotal() > 1) {
            String groupId = UUID.randomUUID().toString();
            LocalDate baseDate = LocalDate.parse(dto.getDate());
            for (int i = 1; i <= dto.getInstallmentTotal(); i++) {
                LocalDate installmentDate = baseDate.plusMonths(i - 1);
                boolean dateReached = !installmentDate.isAfter(LocalDate.now());
                Transaction t = buildTransaction(dto, account, category);
                t.setInstallmentTotal(dto.getInstallmentTotal());
                t.setInstallmentNumber(i);
                t.setInstallmentGroupId(groupId);
                t.setDate(installmentDate.toString());
                t.setFixed(false);
                t.setBalanceAdjusted(dateReached);
                created.add(repository.save(t));
                if (dateReached) {
                    accountService.adjustBalance(account, computeDelta(dto.getType(), dto.getAmount()));
                }
            }
            return created.stream().map(this::toDTO).toList();
        } else {
            boolean dateReached = !LocalDate.parse(dto.getDate()).isAfter(LocalDate.now());
            Transaction t = buildTransaction(dto, account, category);
            t.setBalanceAdjusted(dateReached);
            created.add(repository.save(t));

            if (dto.isFixed()) {
                LocalDate baseDate = LocalDate.parse(dto.getDate());
                for (int i = 1; i <= 11; i++) {
                    Transaction future = buildTransaction(dto, account, category);
                    future.setDate(baseDate.plusMonths(i).toString());
                    future.setBalanceAdjusted(false);
                    repository.save(future);
                }
            }

            if (dateReached) {
                accountService.adjustBalance(account, computeDelta(dto.getType(), dto.getAmount()));
            }
            return created.stream().map(this::toDTO).toList();
        }
    }

    @Transactional
    public TransactionDTO update(Long id, TransactionDTO dto) {
        Transaction existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        Account newAccount = accountRepository.findById(dto.getAccountId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        Category newCategory = dto.getCategoryId() != null
                ? categoryRepository.findById(dto.getCategoryId()).orElse(null)
                : null;

        boolean wasAdjusted = existing.isBalanceAdjusted();
        boolean newDateReached = !LocalDate.parse(dto.getDate()).isAfter(LocalDate.now());

        // Reverte o saldo antigo apenas se ele foi aplicado
        if (wasAdjusted) {
            accountService.adjustBalance(existing.getAccount(),
                    computeDelta(existing.getType(), existing.getAmount()).negate());
        }

        existing.setAccount(newAccount);
        existing.setCategory(newCategory);
        existing.setType(dto.getType());
        existing.setPaymentType(dto.getPaymentType());
        existing.setAmount(dto.getAmount());
        existing.setDescription(dto.getDescription());
        existing.setDate(dto.getDate());
        existing.setFixed(dto.isFixed());
        existing.setBalanceAdjusted(newDateReached);
        repository.save(existing);

        // Aplica o novo saldo apenas se a nova data já chegou
        if (newDateReached) {
            accountService.adjustBalance(newAccount, computeDelta(dto.getType(), dto.getAmount()));
        }

        return toDTO(existing);
    }

    @Transactional
    public void delete(Long id, String scope) {
        Transaction t = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));

        if ("GROUP".equals(scope) && t.getInstallmentGroupId() != null) {
            List<Transaction> group = repository.findByInstallmentGroupId(t.getInstallmentGroupId());
            group.stream().filter(Transaction::isBalanceAdjusted).forEach(tx ->
                    accountService.adjustBalance(tx.getAccount(), computeDelta(tx.getType(), tx.getAmount()).negate()));
            repository.deleteAll(group);
        } else if ("FUTURE".equals(scope)) {
            if (t.getInstallmentGroupId() != null) {
                List<Transaction> future = repository.findByInstallmentGroupIdAndDateGreaterThanEqual(
                        t.getInstallmentGroupId(), t.getDate());
                future.stream().filter(Transaction::isBalanceAdjusted).forEach(tx ->
                        accountService.adjustBalance(tx.getAccount(), computeDelta(tx.getType(), tx.getAmount()).negate()));
                repository.deleteAll(future);
            } else if (t.isFixed()) {
                List<Transaction> future = repository.findByFixedTrueAndDateGreaterThanEqual(t.getDate());
                future.stream().filter(Transaction::isBalanceAdjusted).forEach(tx ->
                        accountService.adjustBalance(tx.getAccount(), computeDelta(tx.getType(), tx.getAmount()).negate()));
                repository.deleteAll(future);
            } else {
                if (t.isBalanceAdjusted()) {
                    accountService.adjustBalance(t.getAccount(), computeDelta(t.getType(), t.getAmount()).negate());
                }
                repository.delete(t);
            }
        } else {
            if (t.isBalanceAdjusted()) {
                accountService.adjustBalance(t.getAccount(), computeDelta(t.getType(), t.getAmount()).negate());
            }
            repository.delete(t);
        }
    }

    private Transaction buildTransaction(TransactionDTO dto, Account account, Category category) {
        return Transaction.builder()
                .account(account)
                .category(category)
                .type(dto.getType())
                .paymentType(dto.getPaymentType())
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .date(dto.getDate())
                .fixed(dto.isFixed())
                .build();
    }

    private BigDecimal computeDelta(TransactionType type, BigDecimal amount) {
        return type == TransactionType.INCOME ? amount : amount.negate();
    }

    public TransactionDTO toDTO(Transaction t) {
        TransactionDTO dto = new TransactionDTO();
        dto.setId(t.getId());
        dto.setAccountId(t.getAccount().getId());
        dto.setType(t.getType());
        dto.setPaymentType(t.getPaymentType());
        dto.setAmount(t.getAmount());
        dto.setDescription(t.getDescription());
        dto.setDate(t.getDate());
        dto.setFixed(t.isFixed());
        dto.setInstallmentTotal(t.getInstallmentTotal());
        dto.setInstallmentNumber(t.getInstallmentNumber());
        dto.setInstallmentGroupId(t.getInstallmentGroupId());

        AccountDTO accountDTO = new AccountDTO();
        accountDTO.setId(t.getAccount().getId());
        accountDTO.setName(t.getAccount().getName());
        accountDTO.setType(t.getAccount().getType());
        accountDTO.setBalance(t.getAccount().getBalance());
        accountDTO.setColor(t.getAccount().getColor());
        accountDTO.setIcon(t.getAccount().getIcon());
        dto.setAccount(accountDTO);

        if (t.getCategory() != null) {
            CategoryDTO catDTO = new CategoryDTO();
            catDTO.setId(t.getCategory().getId());
            catDTO.setName(t.getCategory().getName());
            catDTO.setType(t.getCategory().getType());
            catDTO.setColor(t.getCategory().getColor());
            catDTO.setIcon(t.getCategory().getIcon());
            dto.setCategory(catDTO);
            dto.setCategoryId(t.getCategory().getId());
        }

        return dto;
    }
}
