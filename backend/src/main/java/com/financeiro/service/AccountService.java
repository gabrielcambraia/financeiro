package com.financeiro.service;

import com.financeiro.dto.AccountDTO;
import com.financeiro.entity.Account;
import com.financeiro.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository repository;

    public List<AccountDTO> findAll() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    public AccountDTO create(AccountDTO dto) {
        Account account = Account.builder()
                .name(dto.getName())
                .type(dto.getType())
                .balance(dto.getBalance())
                .color(dto.getColor())
                .icon(dto.getIcon())
                .build();
        return toDTO(repository.save(account));
    }

    public AccountDTO update(Long id, AccountDTO dto) {
        Account account = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada: " + id));
        account.setName(dto.getName());
        account.setType(dto.getType());
        account.setBalance(dto.getBalance());
        account.setColor(dto.getColor());
        account.setIcon(dto.getIcon());
        return toDTO(repository.save(account));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public AccountDTO toDTO(Account a) {
        AccountDTO dto = new AccountDTO();
        dto.setId(a.getId());
        dto.setName(a.getName());
        dto.setType(a.getType());
        dto.setBalance(a.getBalance());
        dto.setColor(a.getColor());
        dto.setIcon(a.getIcon());
        return dto;
    }

    public void adjustBalance(Account account, java.math.BigDecimal delta) {
        account.setBalance(account.getBalance().add(delta));
        repository.save(account);
    }
}
