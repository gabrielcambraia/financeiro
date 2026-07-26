package com.financeiro.repository;

import com.financeiro.entity.Meta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetaRepository extends JpaRepository<Meta, Long> {

    List<Meta> findByEspacoIdOrderByCriadoEmDesc(Long espacoId);

    Optional<Meta> findByIdAndEspacoId(Long id, Long espacoId);
}
