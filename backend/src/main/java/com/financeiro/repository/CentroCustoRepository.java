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

    boolean existsByEspacoIdAndNomeAndEntidadeIdIsNull(Long espacoId, String nome);

    boolean existsByEspacoIdAndNomeAndEntidadeId(Long espacoId, String nome, Long entidadeId);

    boolean existsByEspacoIdAndNomeAndEntidadeIdIsNullAndIdNot(Long espacoId, String nome, Long id);

    boolean existsByEspacoIdAndNomeAndEntidadeIdAndIdNot(Long espacoId, String nome, Long entidadeId, Long id);

    @Query("SELECT c FROM CentroCusto c WHERE c.espacoId = :espacoId " +
           "AND (:entidadeId IS NULL OR c.entidadeId = :entidadeId OR c.entidadeId IS NULL)")
    List<CentroCusto> findByEspacoIdFiltradoPorEntidade(@Param("espacoId") Long espacoId,
                                                         @Param("entidadeId") Long entidadeId);
}
