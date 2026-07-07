package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
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
    private final ContextoEspaco contextoEspaco;

    public List<CategoriaDTO> findAll(TipoTransacao tipo) {
        Long espacoId = contextoEspaco.espacoAtual();
        List<Categoria> list = tipo != null
                ? repository.findByTipoAndEspacoId(tipo, espacoId)
                : repository.findByEspacoId(espacoId);
        return list.stream().map(this::toDTO).toList();
    }

    public CategoriaDTO create(CategoriaDTO dto) {
        Categoria cat = Categoria.builder()
                .nome(dto.getNome())
                .tipo(dto.getTipo())
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .espacoId(contextoEspaco.espacoAtual())
                .build();
        return toDTO(repository.save(cat));
    }

    public CategoriaDTO update(Long id, CategoriaDTO dto) {
        Categoria cat = repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada: " + id));
        cat.setNome(dto.getNome());
        cat.setTipo(dto.getTipo());
        cat.setCor(dto.getCor());
        cat.setIcone(dto.getIcone());
        return toDTO(repository.save(cat));
    }

    public void delete(Long id) {
        Categoria cat = repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada: " + id));
        repository.delete(cat);
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
