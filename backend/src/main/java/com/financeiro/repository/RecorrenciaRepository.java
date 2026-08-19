package com.financeiro.repository;

import com.financeiro.entity.Recorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecorrenciaRepository extends JpaRepository<Recorrencia, Long> {

    Optional<Recorrencia> findByIdAndEspacoId(Long id, Long espacoId);

    // LEFT JOINs explícitos evitam INNER JOINs implícitos que excluiriam
    // recorrências de débito (cartao_id=NULL) ou crédito (conta_id=NULL).
    @Query("SELECT r FROM Recorrencia r " +
           "LEFT JOIN r.conta c " +
           "LEFT JOIN r.cartao cartao " +
           "LEFT JOIN cartao.contaPagamento cp " +
           "WHERE r.espacoId = :espacoId " +
           "AND (:entidadeId IS NULL " +
           "  OR (c IS NOT NULL AND (c.entidadeId = :entidadeId OR c.entidadeId IS NULL)) " +
           "  OR (cp IS NOT NULL AND (cp.entidadeId = :entidadeId OR cp.entidadeId IS NULL))) " +
           "ORDER BY r.ativa DESC, r.criadoEm DESC")
    List<Recorrencia> findByEspacoIdFiltrado(@Param("espacoId") Long espacoId,
                                              @Param("entidadeId") Long entidadeId);

    // Scheduler: todas as recorrências ativas cujo período abrange o mês alvo
    @Query("SELECT r FROM Recorrencia r WHERE r.ativa = true " +
           "AND r.dataInicio <= :fimMes " +
           "AND (r.dataFim IS NULL OR r.dataFim >= :inicioMes)")
    List<Recorrencia> findAtivasParaMes(@Param("inicioMes") LocalDate inicioMes,
                                         @Param("fimMes") LocalDate fimMes);
}
