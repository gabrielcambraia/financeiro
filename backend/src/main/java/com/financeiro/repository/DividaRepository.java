package com.financeiro.repository;

import com.financeiro.entity.Divida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DividaRepository extends JpaRepository<Divida, Long> {

    List<Divida> findByEspacoIdOrderByCriadoEmDesc(Long espacoId);

    Optional<Divida> findByIdAndEspacoId(Long id, Long espacoId);
}
