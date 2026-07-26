package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.entity.Cartao;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.ItemFatura;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ItemFaturaService {

    private final ItemFaturaRepository repository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;

    public List<ItemFaturaDTO> findAbertos(Long cartaoId) {
        Long espacoId = contextoEspaco.espacoAtual();
        return repository.findByEspacoIdAndCartaoIdAndFaturaIdIsNullOrderByDataDesc(espacoId, cartaoId)
                .stream().map(this::toDTO).toList();
    }

    public List<ItemFaturaDTO> findByFilters(Long cartaoId, String month) {
        Long espacoId = contextoEspaco.espacoAtual();
        if (month == null) {
            return findAbertos(cartaoId);
        }
        YearMonth ym = YearMonth.parse(month);
        return repository.findByEspacoIdAndCartaoIdAndDataBetweenOrderByDataDesc(
                espacoId, cartaoId, ym.atDay(1), ym.atEndOfMonth()).stream().map(this::toDTO).toList();
    }

    @Transactional
    public List<ItemFaturaDTO> create(ItemFaturaDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioId = contextoUsuario.usuarioAtual();
        Cartao cartao = cartaoRepository.findByIdAndEspacoId(dto.getCartaoId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Cartão não encontrado"));
        Categoria categoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        List<ItemFatura> criados = new ArrayList<>();

        if (dto.getTotalParcelas() != null && dto.getTotalParcelas() > 1) {
            String grupoId = UUID.randomUUID().toString();
            LocalDate dataBase = dto.getData();
            for (int i = 1; i <= dto.getTotalParcelas(); i++) {
                ItemFatura item = buildItem(dto, cartao, categoria, espacoId, usuarioId);
                item.setData(dataBase.plusMonths(i - 1));
                item.setTotalParcelas(dto.getTotalParcelas());
                item.setNumeroParcela(i);
                item.setGrupoParcelaId(grupoId);
                criados.add(repository.save(item));
            }
        } else {
            criados.add(repository.save(buildItem(dto, cartao, categoria, espacoId, usuarioId)));
        }

        return criados.stream().map(this::toDTO).toList();
    }

    @Transactional
    public ItemFaturaDTO update(Long id, ItemFaturaDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        ItemFatura item = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Item não encontrado"));
        garantirNaoFaturado(item);

        Categoria categoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        item.setCategoria(categoria);
        item.setValor(dto.getValor());
        item.setDescricao(dto.getDescricao());
        item.setData(dto.getData());
        return toDTO(repository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        ItemFatura item = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Item não encontrado"));
        garantirNaoFaturado(item);
        repository.delete(item);
    }

    @Transactional
    public ItemFaturaDTO cancelar(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        ItemFatura item = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Item não encontrado"));
        garantirNaoFaturado(item);
        item.setDataCancelamento(LocalDate.now());
        return toDTO(repository.save(item));
    }

    private void garantirNaoFaturado(ItemFatura item) {
        if (item.getFatura() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este item já faz parte de uma fatura fechada e não pode mais ser alterado");
        }
    }

    private ItemFatura buildItem(ItemFaturaDTO dto, Cartao cartao, Categoria categoria, Long espacoId, Long usuarioId) {
        return ItemFatura.builder()
                .cartao(cartao)
                .categoria(categoria)
                .valor(dto.getValor())
                .descricao(dto.getDescricao())
                .data(dto.getData())
                .espacoId(espacoId)
                .usuarioId(usuarioId)
                .build();
    }

    public ItemFaturaDTO toDTO(ItemFatura i) {
        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setId(i.getId());
        dto.setCartaoId(i.getCartao().getId());
        dto.setValor(i.getValor());
        dto.setDescricao(i.getDescricao());
        dto.setData(i.getData());
        dto.setTotalParcelas(i.getTotalParcelas());
        dto.setNumeroParcela(i.getNumeroParcela());
        dto.setGrupoParcelaId(i.getGrupoParcelaId());
        dto.setDataCancelamento(i.getDataCancelamento());
        dto.setCancelado(i.getDataCancelamento() != null);
        dto.setFaturaId(i.getFatura() != null ? i.getFatura().getId() : null);
        dto.setFaturado(i.getFatura() != null);

        if (i.getCategoria() != null) {
            CategoriaDTO catDTO = new CategoriaDTO();
            catDTO.setId(i.getCategoria().getId());
            catDTO.setNome(i.getCategoria().getNome());
            catDTO.setTipo(i.getCategoria().getTipo());
            catDTO.setCor(i.getCategoria().getCor());
            catDTO.setIcone(i.getCategoria().getIcone());
            dto.setCategoria(catDTO);
            dto.setCategoriaId(i.getCategoria().getId());
        }

        return dto;
    }
}
