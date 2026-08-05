package com.financeiro.repository;

import com.financeiro.entity.MovimentacaoAtivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MovimentacaoAtivoRepository extends JpaRepository<MovimentacaoAtivo, Long> {

    List<MovimentacaoAtivo> findByEspacoIdAndAtivoIdOrderByDataDesc(Long espacoId, Long ativoId);

    List<MovimentacaoAtivo> findByEspacoIdAndDataAfter(Long espacoId, LocalDate data);

    /** Usado por AtivoService.findAll() para preencher campos derivados (rentabilidade,
     * IR estimado) numa única query — evita N+1 buscando movimentação por ativo. */
    List<MovimentacaoAtivo> findByEspacoId(Long espacoId);

    List<MovimentacaoAtivo> findByAtivoIdOrderByDataAsc(Long ativoId);

    Optional<MovimentacaoAtivo> findByTransacaoId(Long transacaoId);

    @Modifying
    @Query("DELETE FROM MovimentacaoAtivo m WHERE m.ativo.id = :ativoId")
    void deleteByAtivoId(@Param("ativoId") Long ativoId);
}
