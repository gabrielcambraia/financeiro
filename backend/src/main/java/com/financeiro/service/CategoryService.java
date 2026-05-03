package com.financeiro.service;

import com.financeiro.dto.CategoryDTO;
import com.financeiro.entity.Category;
import com.financeiro.entity.enums.TransactionType;
import com.financeiro.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;

    public List<CategoryDTO> findAll(TransactionType type) {
        List<Category> list = type != null ? repository.findByType(type) : repository.findAll();
        return list.stream().map(this::toDTO).toList();
    }

    public CategoryDTO create(CategoryDTO dto) {
        Category cat = Category.builder()
                .name(dto.getName())
                .type(dto.getType())
                .color(dto.getColor())
                .icon(dto.getIcon())
                .build();
        return toDTO(repository.save(cat));
    }

    public CategoryDTO update(Long id, CategoryDTO dto) {
        Category cat = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada: " + id));
        cat.setName(dto.getName());
        cat.setType(dto.getType());
        cat.setColor(dto.getColor());
        cat.setIcon(dto.getIcon());
        return toDTO(repository.save(cat));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public CategoryDTO toDTO(Category c) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setType(c.getType());
        dto.setColor(c.getColor());
        dto.setIcon(c.getIcon());
        return dto;
    }
}
