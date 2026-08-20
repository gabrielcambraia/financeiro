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

    // LEFT JOIN FETCH evita INNER JOINs implícitos que excluiriam recorrências de
    // débito (cartao_id=NULL) ou crédito (conta_id=NULL), e evita o N+1 do
    // carregamento EAGER de conta/cartao/categoria/centroCusto linha a linha.
    @Query("SELECT r FROM Recorrencia r " +
           "LEFT JOIN FETCH r.conta c " +
           "LEFT JOIN FETCH r.cartao cartao " +
           "LEFT JOIN FETCH r.categoria " +
           "LEFT JOIN FETCH r.centroCusto " +
           "LEFT JOIN cartao.contaPagamento cp " +
           "WHERE r.espacoId = :espacoId " +
           "AND (:entidadeId IS NULL " +
           "  OR (c IS NOT NULL AND (c.entidadeId = :entidadeId OR c.entidadeId IS NULL)) " +
           "  OR (cp IS NOT NULL AND (cp.entidadeId = :entidadeId OR cp.entidadeId IS NULL))) " +
           "ORDER BY r.ativa DESC, r.criadoEm DESC")
    List<Recorrencia> findByEspacoIdFiltrado(@Param("espacoId") Long espacoId,
                                              @Param("entidadeId") Long entidadeId);

    // Scheduler: todas as recorrências ativas cujo período abrange o mês alvo.
    // FETCH nas associações EAGER evita N+1 num job que roda para todos os tenants.
    @Query("SELECT r FROM Recorrencia r " +
           "LEFT JOIN FETCH r.conta " +
           "LEFT JOIN FETCH r.cartao " +
           "LEFT JOIN FETCH r.categoria " +
           "LEFT JOIN FETCH r.centroCusto " +
           "WHERE r.ativa = true " +
           "AND r.dataInicio <= :fimMes " +
           "AND (r.dataFim IS NULL OR r.dataFim >= :inicioMes)")
    List<Recorrencia> findAtivasParaMes(@Param("inicioMes") LocalDate inicioMes,
                                         @Param("fimMes") LocalDate fimMes);

    // Usado pelo AgendadorRecorrencia para saber a partir de qual mês reprocessar
    // (recuperação de meses perdidos com o app fora do ar). Só considera
    // recorrências ainda vigentes hoje — uma recorrência já expirada (dataFim no
    // passado) não é retroativamente reprocessada, mesmo que tenha ficado sem
    // gerar lançamento em algum mês do seu período (mesmo comportamento de antes
    // da recuperação de meses perdidos existir).
    @Query("SELECT MIN(r.dataInicio) FROM Recorrencia r " +
           "WHERE r.ativa = true AND (r.dataFim IS NULL OR r.dataFim >= :hoje)")
    Optional<LocalDate> findDataInicioMaisAntigaAtivaVigente(@Param("hoje") LocalDate hoje);
}
