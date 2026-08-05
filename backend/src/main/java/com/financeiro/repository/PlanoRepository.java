package com.financeiro.repository;

import com.financeiro.entity.Plano;
import com.financeiro.entity.enums.CodigoPlano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanoRepository extends JpaRepository<Plano, Long> {
    Optional<Plano> findByCodigo(CodigoPlano codigo);
    List<Plano> findByAtivoTrue();
}
