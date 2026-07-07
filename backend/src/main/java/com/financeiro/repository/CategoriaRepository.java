package com.financeiro.repository;

import com.financeiro.entity.Categoria;
import com.financeiro.entity.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByTipo(TipoTransacao tipo);
}
