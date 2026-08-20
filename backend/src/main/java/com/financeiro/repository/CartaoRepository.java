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
           "AND (:entidadeId IS NULL OR c.contaPagamento.entidadeId = :entidadeId " +
           "  OR c.contaPagamento.entidadeId IS NULL)")
    List<Cartao> findByEspacoIdFiltradoPorEntidade(@Param("espacoId") Long espacoId,
                                                   @Param("entidadeId") Long entidadeId);
}
