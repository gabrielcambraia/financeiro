package com.financeiro.repository;

import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoTransacao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    Optional<Transacao> findByIdAndEspacoId(Long id, Long espacoId);

    List<Transacao> findByEspacoIdAndDataBetweenOrderByDataDesc(Long espacoId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenOrderByDataDesc(Long espacoId, Long contaId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndDataBetweenOrderByDataAsc(Long espacoId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenOrderByDataAsc(Long espacoId, Long contaId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndCategoriaIdAndDataBetween(Long espacoId, Long categoriaId, LocalDate start, LocalDate end);

    // Calendário: agenda por vencimento, não por competência (findByEspacoIdAndDataBetween*).
    List<Transacao> findByEspacoIdAndDataVencimentoBetweenOrderByDataVencimentoAsc(Long espacoId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndGrupoParcelaId(Long espacoId, String grupoId);

    List<Transacao> findByEspacoIdAndTransferenciaId(Long espacoId, String transferenciaId);

    List<Transacao> findByEspacoIdAndGrupoParcelaIdAndDataGreaterThanEqual(Long espacoId, String grupoId, LocalDate fromDate);

    List<Transacao> findByEspacoIdAndFixaTrueAndDataGreaterThanEqual(Long espacoId, LocalDate fromDate);

    // Método global usado apenas pelo AgendadorTransacaoFixa (job em background,
    // sem contexto de espaço — processa todos os espaços e propaga o espacoId de
    // cada linha de origem para as cópias que cria).
    List<Transacao> findByFixaTrueAndDataBetween(LocalDate start, LocalDate end);

    boolean existsByEspacoIdAndFixaTrueAndContaIdAndValorAndTipoAndDescricaoAndDataBetween(
            Long espacoId, Long contaId, java.math.BigDecimal valor,
            com.financeiro.entity.enums.TipoTransacao tipo,
            String descricao, LocalDate start, LocalDate end);

    // Painel: widget de vencimentos. "Vencidas" não tem limite inferior de
    // data (pode remontar a anos de histórico), então em vez de trazer a
    // lista inteira pra cortar em memória: contagem/soma agregadas (baratas,
    // sem materializar linhas) + só os N mais antigos via Pageable pra exibição.
    long countByEspacoIdAndTipoAndDataVencimentoBeforeAndDataPagamentoIsNullAndDataCancelamentoIsNull(
            Long espacoId, TipoTransacao tipo, LocalDate hoje);

    @Query("select coalesce(sum(t.valor), 0) from Transacao t where t.espacoId = :espacoId and t.tipo = :tipo "
            + "and t.dataVencimento < :hoje and t.dataPagamento is null and t.dataCancelamento is null")
    BigDecimal somaVencidas(@Param("espacoId") Long espacoId, @Param("tipo") TipoTransacao tipo, @Param("hoje") LocalDate hoje);

    List<Transacao> findByEspacoIdAndTipoAndDataVencimentoBeforeAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
            Long espacoId, TipoTransacao tipo, LocalDate hoje, Pageable pageable);

    List<Transacao> findByEspacoIdAndTipoAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
            Long espacoId, TipoTransacao tipo, LocalDate inicio, LocalDate fim);

    // "Ver todas" do bloco de vencimentos: filtra por período de vencimento
    // (em vez de competência), sempre excluindo canceladas e já pagas — mesmo
    // critério de "pendente" usado em PainelService.buildVencimentos.
    List<Transacao> findByEspacoIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
            Long espacoId, LocalDate inicio, LocalDate fim);

    List<Transacao> findByEspacoIdAndContaIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
            Long espacoId, Long contaId, LocalDate inicio, LocalDate fim);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.data BETWEEN :inicio AND :fim " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.data DESC")
    List<Transacao> findByEspacoIdAndDataBetweenFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("entidadeId") Long entidadeId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.data BETWEEN :inicio AND :fim " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.data ASC")
    List<Transacao> findByEspacoIdAndDataBetweenAscFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("entidadeId") Long entidadeId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.conta.id = :contaId AND t.data BETWEEN :inicio AND :fim " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.data DESC")
    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("entidadeId") Long entidadeId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.conta.id = :contaId AND t.data BETWEEN :inicio AND :fim " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.data ASC")
    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenAscFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("entidadeId") Long entidadeId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.dataVencimento <= :limite " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findVencimentosPendentesFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("limite") LocalDate limite,
            @Param("entidadeId") Long entidadeId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.dataVencimento BETWEEN :inicio AND :fim " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findVencimentosPorPeriodoFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("entidadeId") Long entidadeId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.conta.id = :contaId " +
           "AND t.dataVencimento BETWEEN :inicio AND :fim " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findByEspacoIdAndContaIdAndDataVencimentoBetweenFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("entidadeId") Long entidadeId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.dataVencimento BETWEEN :inicio AND :fim " +
           "AND (:entidadeId IS NULL OR t.entidadeId = :entidadeId OR t.entidadeId IS NULL) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findByEspacoIdAndDataVencimentoBetweenFiltradoPorEntidade(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("entidadeId") Long entidadeId);

    List<Transacao> findByEspacoIdAndMetaIdAndDataCancelamentoIsNull(Long espacoId, Long metaId);

    List<Transacao> findByEspacoIdAndMetaId(Long espacoId, Long metaId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Transacao t SET t.meta = null WHERE t.meta.id = :metaId")
    void desvincularMeta(@Param("metaId") Long metaId);
}
