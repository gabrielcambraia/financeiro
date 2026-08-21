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

    // --- série de fixas ---

    List<Transacao> findByEspacoIdAndOrigemFixaId(Long espacoId, Long origemFixaId);

    List<Transacao> findByEspacoIdAndOrigemFixaIdAndDataGreaterThanEqual(Long espacoId, Long origemFixaId, LocalDate fromDate);

    boolean existsByEspacoIdAndOrigemFixaIdAndDataBetween(Long espacoId, Long origemFixaId, LocalDate start, LocalDate end);

    // Usado pelo AgendadorTransacaoFixa: retorna os espaços que têm fixas ativas no
    // período e, em seguida, carrega as fixas espaço a espaço para não materializar
    // todos os registros de todos os tenants em memória de uma vez.
    @Query("SELECT DISTINCT t.espacoId FROM Transacao t " +
           "WHERE t.fixa = true AND t.serieAtiva = true AND t.data BETWEEN :inicio AND :fim")
    List<Long> findEspacosComFixaAtivaNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    List<Transacao> findByEspacoIdAndFixaTrueAndSerieAtivaTrueAndDataBetween(
            Long espacoId, LocalDate inicio, LocalDate fim);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Transacao t SET t.serieAtiva = false WHERE t.espacoId = :espacoId AND t.origemFixaId = :origemFixaId AND t.data < :dataCorte")
    void encerrarTrilhoAntigo(@Param("espacoId") Long espacoId, @Param("origemFixaId") Long origemFixaId, @Param("dataCorte") LocalDate dataCorte);

    // Painel: widget de vencimentos. "Vencidas" não tem limite inferior de
    // data (pode remontar a anos de histórico), então em vez de trazer a
    // lista inteira pra cortar em memória: contagem/soma agregadas (baratas,
    // sem materializar linhas) + só os N mais antigos via Pageable pra exibição.
    // contaId é opcional (null = todas as contas do espaço).
    @Query("select count(t) from Transacao t where t.espacoId = :espacoId and t.tipo = :tipo "
            + "and (:contaId is null or t.conta.id = :contaId) "
            + "and t.dataVencimento < :hoje and t.dataPagamento is null and t.dataCancelamento is null")
    long countVencidas(@Param("espacoId") Long espacoId, @Param("contaId") Long contaId,
                        @Param("tipo") TipoTransacao tipo, @Param("hoje") LocalDate hoje);

    @Query("select coalesce(sum(t.valor), 0) from Transacao t where t.espacoId = :espacoId and t.tipo = :tipo "
            + "and (:contaId is null or t.conta.id = :contaId) "
            + "and t.dataVencimento < :hoje and t.dataPagamento is null and t.dataCancelamento is null")
    BigDecimal somaVencidas(@Param("espacoId") Long espacoId, @Param("contaId") Long contaId,
                             @Param("tipo") TipoTransacao tipo, @Param("hoje") LocalDate hoje);

    @Query("select t from Transacao t where t.espacoId = :espacoId and t.tipo = :tipo "
            + "and (:contaId is null or t.conta.id = :contaId) "
            + "and t.dataVencimento < :hoje and t.dataPagamento is null and t.dataCancelamento is null "
            + "order by t.dataVencimento asc")
    List<Transacao> findVencidas(@Param("espacoId") Long espacoId, @Param("contaId") Long contaId,
                                  @Param("tipo") TipoTransacao tipo, @Param("hoje") LocalDate hoje, Pageable pageable);

    @Query("select t from Transacao t where t.espacoId = :espacoId and t.tipo = :tipo "
            + "and (:contaId is null or t.conta.id = :contaId) "
            + "and t.dataVencimento between :inicio and :fim and t.dataPagamento is null and t.dataCancelamento is null "
            + "order by t.dataVencimento asc")
    List<Transacao> findAVencer(@Param("espacoId") Long espacoId, @Param("contaId") Long contaId,
                                 @Param("tipo") TipoTransacao tipo, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    // "Ver todas" do bloco de vencimentos: filtra por período de vencimento
    // (em vez de competência), sempre excluindo canceladas e já pagas — mesmo
    // critério de "pendente" usado em PainelService.buildVencimentos.
    List<Transacao> findByEspacoIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
            Long espacoId, LocalDate inicio, LocalDate fim);

    List<Transacao> findByEspacoIdAndContaIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
            Long espacoId, Long contaId, LocalDate inicio, LocalDate fim);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.data BETWEEN :inicio AND :fim " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.data DESC")
    List<Transacao> findByEspacoIdAndDataBetweenFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("filialId") Long filialId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.data BETWEEN :inicio AND :fim " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.data ASC")
    List<Transacao> findByEspacoIdAndDataBetweenAscFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("filialId") Long filialId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.conta.id = :contaId AND t.data BETWEEN :inicio AND :fim " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.data DESC")
    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("filialId") Long filialId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.conta.id = :contaId AND t.data BETWEEN :inicio AND :fim " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.data ASC")
    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenAscFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("filialId") Long filialId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.dataVencimento <= :limite " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findVencimentosPendentesFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("limite") LocalDate limite,
            @Param("filialId") Long filialId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.dataVencimento BETWEEN :inicio AND :fim " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findVencimentosPorPeriodoFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("filialId") Long filialId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.conta.id = :contaId " +
           "AND t.dataVencimento BETWEEN :inicio AND :fim " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findByEspacoIdAndContaIdAndDataVencimentoBetweenFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("contaId") Long contaId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("filialId") Long filialId);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.dataVencimento BETWEEN :inicio AND :fim " +
           "AND (:filialId IS NULL OR t.conta.filialId = :filialId) " +
           "ORDER BY t.dataVencimento ASC")
    List<Transacao> findByEspacoIdAndDataVencimentoBetweenFiltradoPorFilial(
            @Param("espacoId") Long espacoId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("filialId") Long filialId);

    List<Transacao> findByEspacoIdAndMetaIdAndDataCancelamentoIsNull(Long espacoId, Long metaId);

    List<Transacao> findByEspacoIdAndMetaId(Long espacoId, Long metaId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Transacao t SET t.meta = null WHERE t.meta.id = :metaId")
    void desvincularMeta(@Param("metaId") Long metaId);

    // --- recorrência ---

    boolean existsByOrigemRecorrenciaIdAndDataBetween(Long origemRecorrenciaId, LocalDate inicio, LocalDate fim);

    // --- débito automático ---

    @Query("SELECT DISTINCT t.espacoId FROM Transacao t " +
           "WHERE t.debitoAutomatico = true " +
           "AND t.tipo = com.financeiro.entity.enums.TipoTransacao.DESPESA " +
           "AND t.tipoPagamento = com.financeiro.entity.enums.TipoPagamento.DEBITO " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND t.dataVencimento <= :hoje")
    List<Long> findEspacosComDebitoAutomaticoVencido(@Param("hoje") LocalDate hoje);

    @Query("SELECT t FROM Transacao t WHERE t.espacoId = :espacoId " +
           "AND t.debitoAutomatico = true " +
           "AND t.tipo = com.financeiro.entity.enums.TipoTransacao.DESPESA " +
           "AND t.tipoPagamento = com.financeiro.entity.enums.TipoPagamento.DEBITO " +
           "AND t.dataPagamento IS NULL AND t.dataCancelamento IS NULL " +
           "AND t.dataVencimento <= :hoje")
    List<Transacao> findDebitoAutomaticoAQuitar(@Param("espacoId") Long espacoId, @Param("hoje") LocalDate hoje);
}
