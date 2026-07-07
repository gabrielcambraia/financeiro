package com.financeiro.repository;

import com.financeiro.entity.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    List<Conta> findByEspacoId(Long espacoId);

    Optional<Conta> findByIdAndEspacoId(Long id, Long espacoId);
}
