package com.financeiro.repository;

import com.financeiro.entity.ItemFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ItemFaturaRepository extends JpaRepository<ItemFatura, Long> {

    Optional<ItemFatura> findByIdAndEspacoId(Long id, Long espacoId);

    List<ItemFatura> findByEspacoIdAndCartaoIdAndFaturaIdIsNullOrderByDataDesc(Long espacoId, Long cartaoId);

    List<ItemFatura> findByEspacoIdAndFaturaIdOrderByDataDesc(Long espacoId, Long faturaId);

    List<ItemFatura> findByEspacoIdAndGrupoParcelaId(Long espacoId, String grupoParcelaId);

    // Usado pela tela do cartão: itens em aberto de um cartão dentro da janela
    // de um ciclo de fechamento específico (ver ItemFaturaService.findAbertosPorCiclo).
    List<ItemFatura> findByEspacoIdAndCartaoIdAndFaturaIdIsNullAndDataBetweenOrderByDataDesc(
            Long espacoId, Long cartaoId, LocalDate inicio, LocalDate fim);

    // Usado pelo AgendadorFatura (job global, sem contexto de espaço): soma
    // os itens ainda não faturados, não cancelados e com data até o fechamento
    // do ciclo atual — um item com data futura (ex.: parcela de compra
    // parcelada que cai no mês seguinte) fica em aberto para o PRÓXIMO
    // fechamento, em vez de ser varrido para a fatura de hoje.
    List<ItemFatura> findByCartaoIdAndFaturaIdIsNullAndDataCancelamentoIsNullAndDataLessThanEqual(
            Long cartaoId, LocalDate dataFechamento);

    // Usado pela tela de Lançamentos: compras em aberto (ainda não faturadas)
    // dentro do mês de competência, filtráveis por conta de pagamento do
    // cartão e/ou pelo próprio cartão. Inclui canceladas (ficam visíveis no
    // histórico, mesmo padrão de Transacao) — quem some da lista são só as
    // já faturadas, pois quem as representa a partir daí é a despesa
    // consolidada da fatura.
    @Query("""
        SELECT i FROM ItemFatura i
        WHERE i.espacoId = :espacoId
          AND i.fatura IS NULL
          AND i.data BETWEEN :inicio AND :fim
          AND (:contaId IS NULL OR i.cartao.contaPagamento.id = :contaId)
          AND (:cartaoId IS NULL OR i.cartao.id = :cartaoId)
        ORDER BY i.data DESC
        """)
    List<ItemFatura> buscarAbertosPorFiltro(@Param("espacoId") Long espacoId, @Param("inicio") LocalDate inicio,
                                             @Param("fim") LocalDate fim, @Param("contaId") Long contaId,
                                             @Param("cartaoId") Long cartaoId);

    // --- série de fixas (itens de fatura) ---

    List<ItemFatura> findByEspacoIdAndOrigemFixaIdAndDataGreaterThanEqual(
            Long espacoId, Long origemFixaId, LocalDate data);

    boolean existsByEspacoIdAndOrigemFixaIdAndDataBetween(
            Long espacoId, Long origemFixaId, LocalDate inicio, LocalDate fim);

    List<ItemFatura> findByEspacoIdAndFixaTrueAndSerieAtivaTrueAndDataBetween(
            Long espacoId, LocalDate inicio, LocalDate fim);

    @Query("SELECT DISTINCT i.espacoId FROM ItemFatura i " +
           "WHERE i.fixa = true AND i.serieAtiva = true AND i.data BETWEEN :inicio AND :fim")
    List<Long> findEspacosComItemFixaAtivaNoPeriodo(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Modifying
    @Transactional
    @Query("UPDATE ItemFatura i SET i.serieAtiva = false WHERE i.espacoId = :espacoId AND i.origemFixaId = :origemFixaId AND i.data < :dataCorte")
    void encerrarTrilhoAntigo(@Param("espacoId") Long espacoId, @Param("origemFixaId") Long origemFixaId,
                               @Param("dataCorte") LocalDate dataCorte);
}
