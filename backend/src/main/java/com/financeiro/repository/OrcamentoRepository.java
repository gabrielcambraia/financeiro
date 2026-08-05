package com.financeiro.repository;

import com.financeiro.entity.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrcamentoRepository extends JpaRepository<Orcamento, Long> {

    List<Orcamento> findByEspacoIdAndMes(Long espacoId, String mes);

    Optional<Orcamento> findByIdAndEspacoId(Long id, Long espacoId);

    boolean existsByEspacoIdAndCategoriaIdAndMes(Long espacoId, Long categoriaId, String mes);

    boolean existsByEspacoIdAndCategoriaIdAndMesAndIdNot(Long espacoId, Long categoriaId, String mes, Long id);

    @Query("SELECT o FROM Orcamento o WHERE o.espacoId = :espacoId AND o.mes = :mes " +
           "AND (:entidadeId IS NULL OR o.entidadeId = :entidadeId OR o.entidadeId IS NULL)")
    List<Orcamento> findByEspacoIdAndMesFiltradoPorEntidade(@Param("espacoId") Long espacoId,
                                                            @Param("mes") String mes,
                                                            @Param("entidadeId") Long entidadeId);
}
