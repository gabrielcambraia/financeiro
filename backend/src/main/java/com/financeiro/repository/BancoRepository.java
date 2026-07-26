package com.financeiro.repository;

import com.financeiro.entity.Banco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

// Sem espaco_id: catálogo global, compartilhado por todos os tenants.
public interface BancoRepository extends JpaRepository<Banco, Long> {

    default List<Banco> findAllOrdenado() {
        return findAll(Sort.by("nome"));
    }
}
