package com.financeiro.repository;

import com.financeiro.entity.Category;
import com.financeiro.entity.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByType(TransactionType type);
}
