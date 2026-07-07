package com.financeiro.repository;

import com.financeiro.entity.Categoria;
import com.financeiro.entity.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByEspacoId(Long espacoId);

    List<Categoria> findByTipoAndEspacoId(TipoTransacao tipo, Long espacoId);

    Optional<Categoria> findByIdAndEspacoId(Long id, Long espacoId);
}
