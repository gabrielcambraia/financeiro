package com.financeiro.repository;

import com.financeiro.entity.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {

    List<Orcamento> findByEspacoIdAndMes(Long espacoId, String mes);

    Optional<Orcamento> findByIdAndEspacoId(Long id, Long espacoId);

    boolean existsByEspacoIdAndCategoriaIdAndMes(Long espacoId, Long categoriaId, String mes);

    boolean existsByEspacoIdAndCategoriaIdAndMesAndIdNot(Long espacoId, Long categoriaId, String mes, Long id);
}
