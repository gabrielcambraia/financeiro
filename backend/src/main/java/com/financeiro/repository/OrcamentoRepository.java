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
           "AND (:filialId IS NULL OR o.filialId = :filialId OR o.filialId IS NULL)")
    List<Orcamento> findByEspacoIdAndMesFiltradoPorFilial(@Param("espacoId") Long espacoId,
                                                            @Param("mes") String mes,
                                                            @Param("filialId") Long filialId);
}
