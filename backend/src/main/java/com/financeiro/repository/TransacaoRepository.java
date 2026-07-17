package com.financeiro.repository;

import com.financeiro.entity.Transacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {

    Optional<Transacao> findByIdAndEspacoId(Long id, Long espacoId);

    List<Transacao> findByEspacoIdAndDataBetweenOrderByDataDesc(Long espacoId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenOrderByDataDesc(Long espacoId, Long contaId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndDataBetweenOrderByDataAsc(Long espacoId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndContaIdAndDataBetweenOrderByDataAsc(Long espacoId, Long contaId, LocalDate start, LocalDate end);

    List<Transacao> findByEspacoIdAndGrupoParcelaId(Long espacoId, String grupoId);

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
}
