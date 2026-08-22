package com.financeiro.repository;

import com.financeiro.entity.Ativo;
import com.financeiro.entity.enums.TipoRemuneracao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AtivoRepository extends JpaRepository<Ativo, Long> {

    List<Ativo> findByEspacoIdOrderByCriadoEmDesc(Long espacoId);

    Optional<Ativo> findByIdAndEspacoId(Long id, Long espacoId);

    /**
     * Usado pelo AgendadorRendimento — roda sem contexto de espaço, processa
     * todos de uma vez, mas em lotes ({@code Pageable}) para não segurar todos
     * os ativos da plataforma numa única lista nem numa única unidade de falha.
     * Estável entre páginas porque nenhum campo do predicado (dataCancelamento,
     * remuneracaoTipo) muda durante o processamento — só rendidoAte/valorAtual.
     */
    Page<Ativo> findByDataCancelamentoIsNullAndRemuneracaoTipoNot(TipoRemuneracao remuneracaoTipo, Pageable pageable);

    @Query("SELECT a FROM Ativo a WHERE a.espacoId = :espacoId " +
           "AND (:filialId IS NULL OR a.conta.filialId = :filialId OR a.conta.filialId IS NULL) " +
           "ORDER BY a.criadoEm DESC")
    List<Ativo> findByEspacoIdFiltradoPorFilial(@Param("espacoId") Long espacoId,
                                                  @Param("filialId") Long filialId);
}
