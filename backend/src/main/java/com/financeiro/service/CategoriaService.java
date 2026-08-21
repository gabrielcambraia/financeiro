package com.financeiro.service;

import com.financeiro.context.ContextoFilial;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.entity.Categoria;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
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
    private final ContextoFilial contextoFilial;
    private final ServicoFilial servicoFilial;

    public List<CategoriaDTO> findAll(TipoTransacao tipo) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long filialId = contextoFilial.filialAtual();
        List<Categoria> list;
        if (filialId != null) {
            list = tipo != null
                    ? repository.findByTipoAndEspacoIdFiltradoPorFilial(tipo, espacoId, filialId)
                    : repository.findByEspacoIdFiltradoPorFilial(espacoId, filialId);
        } else {
            list = tipo != null
                    ? repository.findByTipoAndEspacoId(tipo, espacoId)
                    : repository.findByEspacoId(espacoId);
        }
        return list.stream().map(this::toDTO).toList();
    }

    public CategoriaDTO create(CategoriaDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Categoria cat = Categoria.builder()
                .nome(dto.getNome())
                .tipo(dto.getTipo())
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .espacoId(espacoId)
                .filialId(servicoFilial.resolverParaCadastro(dto.getFilialId(), espacoId))
                .build();
        return toDTO(repository.save(cat));
    }

    public CategoriaDTO update(Long id, CategoriaDTO dto) {
        Categoria cat = repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Categoria não encontrada: " + id));
        cat.setNome(dto.getNome());
        cat.setTipo(dto.getTipo());
        cat.setCor(dto.getCor());
        cat.setIcone(dto.getIcone());
        cat.setFilialId(dto.getFilialId());
        return toDTO(repository.save(cat));
    }

    public void delete(Long id) {
        Categoria cat = repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Categoria não encontrada: " + id));
        repository.delete(cat);
    }

    public CategoriaDTO toDTO(Categoria c) {
        CategoriaDTO dto = new CategoriaDTO();
        dto.setId(c.getId());
        dto.setNome(c.getNome());
        dto.setTipo(c.getTipo());
        dto.setCor(c.getCor());
        dto.setIcone(c.getIcone());
        dto.setFilialId(c.getFilialId());
        return dto;
    }
}
