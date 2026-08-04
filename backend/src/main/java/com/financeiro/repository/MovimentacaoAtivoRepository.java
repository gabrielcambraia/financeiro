package com.financeiro.repository;

import com.financeiro.entity.MovimentacaoAtivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MovimentacaoAtivoRepository extends JpaRepository<MovimentacaoAtivo, Long> {

    List<MovimentacaoAtivo> findByEspacoIdAndAtivoIdOrderByDataDesc(Long espacoId, Long ativoId);

    List<MovimentacaoAtivo> findByEspacoIdAndDataAfter(Long espacoId, LocalDate data);

    /** Usado por AtivoService.findAll() para preencher campos derivados (rentabilidade,
     * IR estimado) numa única query — evita N+1 buscando movimentação por ativo. */
    List<MovimentacaoAtivo> findByEspacoId(Long espacoId);

    List<MovimentacaoAtivo> findByAtivoIdOrderByDataAsc(Long ativoId);
}
