package com.financeiro.repository;

import com.financeiro.entity.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {

    Optional<Fatura> findByIdAndEspacoId(Long id, Long espacoId);

    List<Fatura> findByEspacoIdAndCartaoIdOrderByDataFechamentoDesc(Long espacoId, Long cartaoId);

    // Usado pelo AgendadorFatura (job global, sem contexto de espaço) para
    // garantir idempotência: não fechar a mesma fatura duas vezes no mesmo dia.
    boolean existsByCartaoIdAndDataFechamento(Long cartaoId, LocalDate dataFechamento);
}
