package com.financeiro.repository;

import com.financeiro.entity.CentroCusto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CentroCustoRepository extends JpaRepository<CentroCusto, Long> {

    List<CentroCusto> findByEspacoId(Long espacoId);

    Optional<CentroCusto> findByIdAndEspacoId(Long id, Long espacoId);

    boolean existsByEspacoIdAndNomeAndFilialIdIsNull(Long espacoId, String nome);

    boolean existsByEspacoIdAndNomeAndFilialId(Long espacoId, String nome, Long filialId);

    boolean existsByEspacoIdAndNomeAndFilialIdIsNullAndIdNot(Long espacoId, String nome, Long id);

    boolean existsByEspacoIdAndNomeAndFilialIdAndIdNot(Long espacoId, String nome, Long filialId, Long id);

    @Query("SELECT c FROM CentroCusto c WHERE c.espacoId = :espacoId " +
           "AND (:filialId IS NULL OR c.filialId = :filialId OR c.filialId IS NULL)")
    List<CentroCusto> findByEspacoIdFiltradoPorFilial(@Param("espacoId") Long espacoId,
                                                       @Param("filialId") Long filialId);
}
