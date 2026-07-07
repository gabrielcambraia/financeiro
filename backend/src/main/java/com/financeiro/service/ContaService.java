package com.financeiro.service;

import com.financeiro.dto.ContaDTO;
import com.financeiro.entity.Conta;
import com.financeiro.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContaService {

    private final ContaRepository repository;

    public List<ContaDTO> findAll() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    public ContaDTO create(ContaDTO dto) {
        Conta conta = Conta.builder()
                .nome(dto.getNome())
                .tipo(dto.getTipo())
                .saldo(dto.getSaldo())
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .build();
        return toDTO(repository.save(conta));
    }

    public ContaDTO update(Long id, ContaDTO dto) {
        Conta conta = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada: " + id));
        conta.setNome(dto.getNome());
        conta.setTipo(dto.getTipo());
        conta.setSaldo(dto.getSaldo());
        conta.setCor(dto.getCor());
        conta.setIcone(dto.getIcone());
        return toDTO(repository.save(conta));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public ContaDTO toDTO(Conta c) {
        ContaDTO dto = new ContaDTO();
        dto.setId(c.getId());
        dto.setNome(c.getNome());
        dto.setTipo(c.getTipo());
        dto.setSaldo(c.getSaldo());
        dto.setCor(c.getCor());
        dto.setIcone(c.getIcone());
        return dto;
    }

    public void adjustBalance(Conta conta, java.math.BigDecimal delta) {
        conta.setSaldo(conta.getSaldo().add(delta));
        repository.save(conta);
    }
}
