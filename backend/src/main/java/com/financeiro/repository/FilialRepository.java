package com.financeiro.repository;

import com.financeiro.entity.Filial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FilialRepository extends JpaRepository<Filial, Long> {
    List<Filial> findByEspacoId(Long espacoId);
    long countByEspacoId(Long espacoId);
    Optional<Filial> findByEspacoIdAndDocumentoHash(Long espacoId, String documentoHash);
}
