package com.financeiro.repository;

import com.financeiro.entity.Meta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MetaRepository extends JpaRepository<Meta, Long> {

    List<Meta> findByEspacoIdOrderByCriadoEmDesc(Long espacoId);

    Optional<Meta> findByIdAndEspacoId(Long id, Long espacoId);

    @Query("SELECT m FROM Meta m WHERE m.espacoId = :espacoId " +
           "AND (:entidadeId IS NULL OR m.entidadeId = :entidadeId OR m.entidadeId IS NULL) " +
           "ORDER BY m.criadoEm DESC")
    List<Meta> findByEspacoIdFiltradoPorEntidade(@Param("espacoId") Long espacoId,
                                                 @Param("entidadeId") Long entidadeId);
}
