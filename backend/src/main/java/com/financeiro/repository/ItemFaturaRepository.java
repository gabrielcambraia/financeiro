package com.financeiro.repository;

import com.financeiro.entity.ItemFatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ItemFaturaRepository extends JpaRepository<ItemFatura, Long> {

    Optional<ItemFatura> findByIdAndEspacoId(Long id, Long espacoId);

    List<ItemFatura> findByEspacoIdAndCartaoIdAndFaturaIdIsNullOrderByDataDesc(Long espacoId, Long cartaoId);

    List<ItemFatura> findByEspacoIdAndFaturaIdOrderByDataDesc(Long espacoId, Long faturaId);

    List<ItemFatura> findByEspacoIdAndCartaoIdAndDataBetweenOrderByDataDesc(Long espacoId, Long cartaoId, LocalDate inicio, LocalDate fim);

    List<ItemFatura> findByEspacoIdAndGrupoParcelaId(Long espacoId, String grupoParcelaId);

    // Usado pelo AgendadorFatura (job global, sem contexto de espaço): soma
    // tudo que ainda não foi associado a uma fatura fechada e não foi cancelado.
    List<ItemFatura> findByCartaoIdAndFaturaIdIsNullAndDataCancelamentoIsNull(Long cartaoId);
}
