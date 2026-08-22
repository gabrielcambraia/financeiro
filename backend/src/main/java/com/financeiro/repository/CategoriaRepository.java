package com.financeiro.repository;

import com.financeiro.entity.Categoria;
import com.financeiro.entity.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByEspacoId(Long espacoId);

    List<Categoria> findByTipoAndEspacoId(TipoTransacao tipo, Long espacoId);

    Optional<Categoria> findByIdAndEspacoId(Long id, Long espacoId);

    @Query("SELECT c FROM Categoria c WHERE c.espacoId = :espacoId " +
           "AND (:filialId IS NULL OR c.filialId = :filialId OR c.filialId IS NULL)")
    List<Categoria> findByEspacoIdFiltradoPorFilial(@Param("espacoId") Long espacoId,
                                                      @Param("filialId") Long filialId);

    @Query("SELECT c FROM Categoria c WHERE c.tipo = :tipo AND c.espacoId = :espacoId " +
           "AND (:filialId IS NULL OR c.filialId = :filialId OR c.filialId IS NULL)")
    List<Categoria> findByTipoAndEspacoIdFiltradoPorFilial(@Param("tipo") TipoTransacao tipo,
                                                             @Param("espacoId") Long espacoId,
                                                             @Param("filialId") Long filialId);
}
