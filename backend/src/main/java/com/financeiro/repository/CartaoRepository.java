package com.financeiro.repository;

import com.financeiro.entity.Cartao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {

    List<Cartao> findByEspacoId(Long espacoId);

    Optional<Cartao> findByIdAndEspacoId(Long id, Long espacoId);

    boolean existsByBancoId(Long bancoId);
}
