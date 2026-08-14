package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.CentroCustoDTO;
import com.financeiro.entity.CentroCusto;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.CentroCustoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CentroCustoService {

    private final CentroCustoRepository repository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoEntidade contextoEntidade;

    public List<CentroCustoDTO> findAll() {
        Long espacoId = contextoEspaco.espacoAtual();
        Long entidadeId = contextoEntidade.entidadeAtual();
        List<CentroCusto> list = entidadeId != null
                ? repository.findByEspacoIdFiltradoPorEntidade(espacoId, entidadeId)
                : repository.findByEspacoId(espacoId);
        return list.stream().map(this::toDTO).toList();
    }

    @PreAuthorize("@autorizacaoEspaco.exigirDono('Somente o dono do espaço pode criar centros de custo')")
    public CentroCustoDTO create(CentroCustoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        validarNomeUnico(espacoId, dto.getNome(), dto.getEntidadeId(), null);
        CentroCusto cc = CentroCusto.builder()
                .nome(dto.getNome())
                .cor(dto.getCor())
                .espacoId(espacoId)
                .entidadeId(dto.getEntidadeId())
                .build();
        return toDTO(repository.save(cc));
    }

    @PreAuthorize("@autorizacaoEspaco.exigirDono('Somente o dono do espaço pode alterar centros de custo')")
    public CentroCustoDTO update(Long id, CentroCustoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        CentroCusto cc = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Centro de custo não encontrado: " + id));
        validarNomeUnico(espacoId, dto.getNome(), dto.getEntidadeId(), id);
        cc.setNome(dto.getNome());
        cc.setCor(dto.getCor());
        cc.setEntidadeId(dto.getEntidadeId());
        return toDTO(repository.save(cc));
    }

    @PreAuthorize("@autorizacaoEspaco.exigirDono('Somente o dono do espaço pode excluir centros de custo')")
    public void delete(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        CentroCusto cc = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Centro de custo não encontrado: " + id));
        repository.delete(cc);
    }

    public CentroCustoDTO toDTO(CentroCusto cc) {
        CentroCustoDTO dto = new CentroCustoDTO();
        dto.setId(cc.getId());
        dto.setNome(cc.getNome());
        dto.setCor(cc.getCor());
        dto.setEntidadeId(cc.getEntidadeId());
        return dto;
    }

    private void validarNomeUnico(Long espacoId, String nome, Long entidadeId, Long idExcluido) {
        boolean existe;
        if (idExcluido == null) {
            existe = entidadeId == null
                    ? repository.existsByEspacoIdAndNomeAndEntidadeIdIsNull(espacoId, nome)
                    : repository.existsByEspacoIdAndNomeAndEntidadeId(espacoId, nome, entidadeId);
        } else {
            existe = entidadeId == null
                    ? repository.existsByEspacoIdAndNomeAndEntidadeIdIsNullAndIdNot(espacoId, nome, idExcluido)
                    : repository.existsByEspacoIdAndNomeAndEntidadeIdAndIdNot(espacoId, nome, entidadeId, idExcluido);
        }
        if (existe) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe um centro de custo com este nome");
    }
}
