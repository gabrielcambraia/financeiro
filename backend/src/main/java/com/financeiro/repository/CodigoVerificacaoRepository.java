package com.financeiro.repository;

import com.financeiro.entity.CodigoVerificacao;
import com.financeiro.entity.enums.PropositoCodigo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CodigoVerificacaoRepository extends JpaRepository<CodigoVerificacao, Long> {

    Optional<CodigoVerificacao> findFirstByDestinatarioAndPropositoAndUsadoEmIsNullOrderByCriadoEmDesc(
            String destinatario, PropositoCodigo proposito);

    long countByDestinatarioAndPropositoAndCriadoEmAfter(
            String destinatario, PropositoCodigo proposito, LocalDateTime desde);

    int deleteByDestinatarioAndPropositoAndExpiraEmBefore(
            String destinatario, PropositoCodigo proposito, LocalDateTime limite);

    int deleteByExpiraEmBefore(LocalDateTime limite);
}
