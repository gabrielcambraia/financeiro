package com.financeiro.repository;

import com.financeiro.entity.Entidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntidadeRepository extends JpaRepository<Entidade, Long> {
    List<Entidade> findByEspacoId(Long espacoId);
    long countByEspacoId(Long espacoId);
    Optional<Entidade> findByEspacoIdAndDocumentoHash(Long espacoId, String documentoHash);
}
