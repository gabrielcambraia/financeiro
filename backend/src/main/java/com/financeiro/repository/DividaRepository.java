package com.financeiro.repository;

import com.financeiro.entity.Divida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DividaRepository extends JpaRepository<Divida, Long> {

    List<Divida> findByEspacoIdOrderByCriadoEmDesc(Long espacoId);

    Optional<Divida> findByIdAndEspacoId(Long id, Long espacoId);

    @Query("SELECT d FROM Divida d WHERE d.espacoId = :espacoId " +
           "AND (:entidadeId IS NULL OR d.entidadeId = :entidadeId OR d.entidadeId IS NULL) " +
           "ORDER BY d.criadoEm DESC")
    List<Divida> findByEspacoIdFiltradoPorEntidade(@Param("espacoId") Long espacoId,
                                                   @Param("entidadeId") Long entidadeId);

    Optional<Divida> findByEspacoIdAndGrupoParcelaId(Long espacoId, String grupoParcelaId);
}
