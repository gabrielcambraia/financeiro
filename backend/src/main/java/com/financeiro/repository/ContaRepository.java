package com.financeiro.repository;

import com.financeiro.entity.Conta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    List<Conta> findByEspacoId(Long espacoId);

    Optional<Conta> findByIdAndEspacoId(Long id, Long espacoId);

    boolean existsByBancoId(Long bancoId);

    @Query("SELECT c FROM Conta c WHERE c.espacoId = :espacoId " +
           "AND (:filialId IS NULL OR c.filialId = :filialId OR c.filialId IS NULL)")
    List<Conta> findByEspacoIdFiltradoPorFilial(@Param("espacoId") Long espacoId,
                                                  @Param("filialId") Long filialId);
}
