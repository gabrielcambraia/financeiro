package com.financeiro.repository;

import com.financeiro.entity.IndiceEconomico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IndiceEconomicoRepository extends JpaRepository<IndiceEconomico, Long> {

    Optional<IndiceEconomico> findByCodigoAndMes(String codigo, String mes);

    List<IndiceEconomico> findByCodigo(String codigo);

    boolean existsByCodigoAndMes(String codigo, String mes);

    /** Último mês em cache para a série. {@code mes} é {@code yyyy-MM}, então ordem lexicográfica == cronológica. */
    Optional<IndiceEconomico> findTopByCodigoOrderByMesDesc(String codigo);
}
