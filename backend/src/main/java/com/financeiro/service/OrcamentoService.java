package com.financeiro.service;

import com.financeiro.context.ContextoFilial;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.OrcamentoDTO;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Orcamento;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.OrcamentoRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrcamentoService {

    private final OrcamentoRepository repository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoFilial contextoFilial;
    private final ServicoFilial servicoFilial;

    public List<OrcamentoDTO> findByMes(String mes) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long filialId = contextoFilial.filialAtual();
        List<Orcamento> lista = filialId != null
                ? repository.findByEspacoIdAndMesFiltradoPorFilial(espacoId, mes, filialId)
                : repository.findByEspacoIdAndMes(espacoId, mes);
        return lista.stream().map(this::toDTO).toList();
    }

    public OrcamentoDTO create(OrcamentoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Categoria categoria = validarCategoriaDespesa(dto.getCategoriaId(), espacoId);
        validarMes(dto.getMes());

        if (repository.existsByEspacoIdAndCategoriaIdAndMes(espacoId, categoria.getId(), dto.getMes())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um orçamento para essa categoria neste mês");
        }

        Orcamento orcamento = Orcamento.builder()
                .categoria(categoria)
                .mes(dto.getMes())
                .limite(dto.getLimite())
                .espacoId(espacoId)
                .filialId(servicoFilial.resolverParaCadastro(dto.getFilialId(), espacoId))
                .build();
        return toDTO(repository.save(orcamento));
    }

    public OrcamentoDTO update(Long id, OrcamentoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Orcamento orcamento = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Orçamento não encontrado: " + id));
        Categoria categoria = validarCategoriaDespesa(dto.getCategoriaId(), espacoId);
        validarMes(dto.getMes());

        if (repository.existsByEspacoIdAndCategoriaIdAndMesAndIdNot(espacoId, categoria.getId(), dto.getMes(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um orçamento para essa categoria neste mês");
        }

        orcamento.setCategoria(categoria);
        orcamento.setMes(dto.getMes());
        orcamento.setLimite(dto.getLimite());
        return toDTO(repository.save(orcamento));
    }

    public void delete(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Orcamento orcamento = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Orçamento não encontrado: " + id));
        repository.delete(orcamento);
    }

    private Categoria validarCategoriaDespesa(Long categoriaId, Long espacoId) {
        Categoria categoria = categoriaRepository.findByIdAndEspacoId(categoriaId, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Categoria não encontrada"));
        if (categoria.getTipo() != TipoTransacao.DESPESA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Orçamento só se aplica a categorias de despesa");
        }
        return categoria;
    }

    private void validarMes(String mes) {
        try {
            YearMonth.parse(mes);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido (use o formato yyyy-MM)");
        }
    }

    private OrcamentoDTO toDTO(Orcamento o) {
        Long espacoId = o.getEspacoId();
        YearMonth ym = YearMonth.parse(o.getMes());
        LocalDate inicio = ym.atDay(1);
        LocalDate fim = ym.atEndOfMonth();

        BigDecimal gasto = transacaoRepository
                .findByEspacoIdAndCategoriaIdAndDataBetween(espacoId, o.getCategoria().getId(), inicio, fim).stream()
                .filter(t -> t.getTipo() == TipoTransacao.DESPESA && t.getDataCancelamento() == null)
                .map(Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double percentual = o.getLimite().compareTo(BigDecimal.ZERO) == 0 ? 0
                : gasto.divide(o.getLimite(), 6, RoundingMode.DOWN).doubleValue() * 100;

        CategoriaDTO catDTO = new CategoriaDTO();
        catDTO.setId(o.getCategoria().getId());
        catDTO.setNome(o.getCategoria().getNome());
        catDTO.setTipo(o.getCategoria().getTipo());
        catDTO.setCor(o.getCategoria().getCor());
        catDTO.setIcone(o.getCategoria().getIcone());

        OrcamentoDTO dto = new OrcamentoDTO();
        dto.setId(o.getId());
        dto.setCategoriaId(o.getCategoria().getId());
        dto.setCategoria(catDTO);
        dto.setMes(o.getMes());
        dto.setLimite(o.getLimite());
        dto.setGasto(gasto);
        dto.setPercentualUsado(percentual);
        dto.setEstourado(gasto.compareTo(o.getLimite()) > 0);
        dto.setFilialId(o.getFilialId());
        return dto;
    }
}
