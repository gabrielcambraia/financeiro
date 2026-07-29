package com.financeiro.repository;

import com.financeiro.entity.Ativo;
import com.financeiro.entity.enums.TipoRemuneracao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AtivoRepository extends JpaRepository<Ativo, Long> {

    List<Ativo> findByEspacoIdOrderByCriadoEmDesc(Long espacoId);

    Optional<Ativo> findByIdAndEspacoId(Long id, Long espacoId);

    /** Usado pelo AgendadorRendimento — roda sem contexto de espaço, processa todos de uma vez. */
    List<Ativo> findByDataCancelamentoIsNullAndRemuneracaoTipoNot(TipoRemuneracao remuneracaoTipo);
}
