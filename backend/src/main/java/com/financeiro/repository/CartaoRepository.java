package com.financeiro.repository;

import com.financeiro.entity.Cartao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {

    List<Cartao> findByEspacoId(Long espacoId);

    Optional<Cartao> findByIdAndEspacoId(Long id, Long espacoId);

    boolean existsByBancoId(Long bancoId);

    @Query("SELECT c FROM Cartao c WHERE c.espacoId = :espacoId " +
           "AND (:filialId IS NULL OR c.contaPagamento.filialId = :filialId " +
           "  OR c.contaPagamento.filialId IS NULL)")
    List<Cartao> findByEspacoIdFiltradoPorFilial(@Param("espacoId") Long espacoId,
                                                   @Param("filialId") Long filialId);
}
