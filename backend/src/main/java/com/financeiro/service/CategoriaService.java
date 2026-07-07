package com.financeiro.service;

import com.financeiro.dto.CategoriaDTO;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repository;

    public List<CategoriaDTO> findAll(TipoTransacao tipo) {
        List<Categoria> list = tipo != null ? repository.findByTipo(tipo) : repository.findAll();
        return list.stream().map(this::toDTO).toList();
    }

    public CategoriaDTO create(CategoriaDTO dto) {
        Categoria cat = Categoria.builder()
                .nome(dto.getNome())
                .tipo(dto.getTipo())
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .build();
        return toDTO(repository.save(cat));
    }

    public CategoriaDTO update(Long id, CategoriaDTO dto) {
        Categoria cat = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada: " + id));
        cat.setNome(dto.getNome());
        cat.setTipo(dto.getTipo());
        cat.setCor(dto.getCor());
        cat.setIcone(dto.getIcone());
        return toDTO(repository.save(cat));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public CategoriaDTO toDTO(Categoria c) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setId(c.getId());
        dto.setNome(c.getNome());
        dto.setTipo(c.getTipo());
        dto.setCor(c.getCor());
        dto.setIcone(c.getIcone());
        return dto;
    }
}
