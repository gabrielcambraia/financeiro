package com.financeiro.repository;

import com.financeiro.entity.Fatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {

    Optional<Fatura> findByIdAndEspacoId(Long id, Long espacoId);

    List<Fatura> findByEspacoIdAndCartaoIdOrderByDataFechamentoDesc(Long espacoId, Long cartaoId);

    // Usado pela visualização de faturas por mês (tela do cartão e Lançamentos
    // com filtro de cartão) — filtra pelo mês de fechamento.
    List<Fatura> findByEspacoIdAndCartaoIdAndDataFechamentoBetweenOrderByDataFechamentoDesc(
            Long espacoId, Long cartaoId, LocalDate inicio, LocalDate fim);

    // Usado pelo AgendadorFatura (job global, sem contexto de espaço) para
    // garantir idempotência: não fechar a mesma fatura duas vezes no mesmo dia.
    boolean existsByCartaoIdAndDataFechamento(Long cartaoId, LocalDate dataFechamento);

    @Query("SELECT f FROM Fatura f WHERE f.espacoId = :espacoId AND f.cartao.id = :cartaoId " +
           "AND (:entidadeId IS NULL OR f.cartao.contaPagamento.entidadeId = :entidadeId " +
           "  OR f.cartao.contaPagamento.entidadeId IS NULL) " +
           "ORDER BY f.dataFechamento DESC")
    List<Fatura> findByEspacoIdAndCartaoIdFiltradoPorEntidade(@Param("espacoId") Long espacoId,
                                                              @Param("cartaoId") Long cartaoId,
                                                              @Param("entidadeId") Long entidadeId);

    Optional<Fatura> findByTransacaoDespesaId(Long transacaoId);

    // Usado pela resolução de fatura alvo na conversão de lançamentos:
    // cartaoId + dataFechamento identifica unicamente uma fatura por design do scheduler.
    Optional<Fatura> findByCartaoIdAndDataFechamento(Long cartaoId, LocalDate dataFechamento);

    List<Fatura> findByEspacoIdAndDataFechamentoBetweenOrderByDataFechamentoDesc(
            Long espacoId, LocalDate inicio, LocalDate fim);

    List<Fatura> findByEspacoIdOrderByDataFechamentoDesc(Long espacoId);

    @Query("SELECT f FROM Fatura f WHERE f.espacoId = :espacoId " +
           "AND (:entidadeId IS NULL OR f.cartao.contaPagamento.entidadeId = :entidadeId " +
           "  OR f.cartao.contaPagamento.entidadeId IS NULL) " +
           "ORDER BY f.dataFechamento DESC")
    List<Fatura> findByEspacoIdFiltradoPorEntidade(@Param("espacoId") Long espacoId,
                                                   @Param("entidadeId") Long entidadeId);

    @Query("SELECT f FROM Fatura f WHERE f.espacoId = :espacoId " +
           "AND f.dataFechamento BETWEEN :inicio AND :fim " +
           "AND (:entidadeId IS NULL OR f.cartao.contaPagamento.entidadeId = :entidadeId " +
           "  OR f.cartao.contaPagamento.entidadeId IS NULL) " +
           "ORDER BY f.dataFechamento DESC")
    List<Fatura> findByEspacoIdAndDataFechamentoBetweenFiltradoPorEntidade(
            @Param("espacoId") Long espacoId, @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim, @Param("entidadeId") Long entidadeId);
}
