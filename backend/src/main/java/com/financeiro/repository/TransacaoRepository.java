package com.financeiro.repository;

import com.financeiro.entity.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    List<Transacao> findByDataBetweenOrderByDataDesc(String start, String end);

    List<Transacao> findByContaIdAndDataBetweenOrderByDataDesc(Long contaId, String start, String end);

    List<Transacao> findByFixaTrueAndDataBetween(String start, String end);

    List<Transacao> findByGrupoParcelaId(String grupoId);

    List<Transacao> findByGrupoParcelaIdAndDataGreaterThanEqual(String grupoId, String fromDate);

    List<Transacao> findByFixaTrueAndDataGreaterThanEqual(String fromDate);

    List<Transacao> findByFixaTrueAndSaldoAjustadoFalseAndDataBetween(String start, String end);

    List<Transacao> findBySaldoAjustadoFalseAndDataLessThanEqual(String data);

    boolean existsByFixaTrueAndContaIdAndValorAndTipoAndDescricaoAndDataBetween(
            Long contaId, java.math.BigDecimal valor,
            com.financeiro.entity.enums.TipoTransacao tipo,
            String descricao, String start, String end);

    List<Transacao> findByDataBetweenOrderByDataAsc(String start, String end);

    List<Transacao> findByContaIdAndDataBetweenOrderByDataAsc(Long contaId, String start, String end);
}
