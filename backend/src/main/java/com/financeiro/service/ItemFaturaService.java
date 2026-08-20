package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.CentroCustoDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.entity.Cartao;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Fatura;
import com.financeiro.entity.ItemFatura;
import com.financeiro.entity.Transacao;
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
    private final ResolvedorFaturaAlvo resolvedorFaturaAlvo;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;

    // Usado pela tela do cartão: itens em aberto (ainda não faturados) do
    // CICLO DE FECHAMENTO que cai no mês informado — não o mês corrido da
    // data da compra. Uma parcela datada no mês seguinte, por exemplo, só
    // pertence ao ciclo que fecha depois do próximo fechamento; sem essa
    // distinção ela apareceria misturada com compras de um ciclo diferente.
    // Sem month, traz todos os itens em aberto do cartão, sem filtro de ciclo.
    public List<ItemFaturaDTO> findAbertosPorCiclo(Long cartaoId, String month) {
        Long espacoId = contextoEspaco.espacoAtual();
        if (month == null) {
            return repository.findByEspacoIdAndCartaoIdAndFaturaIdIsNullOrderByDataDesc(espacoId, cartaoId)
                    .stream().map(this::toDTO).toList();
        }
        Cartao cartao = cartaoRepository.findByIdAndEspacoId(cartaoId, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Cartão não encontrado"));
        YearMonth alvo = YearMonth.parse(month);
        LocalDate fechamentoAlvo = CalculadoraFechamentoCartao.fechamentoDoMes(cartao, alvo);
        LocalDate fechamentoAnterior = CalculadoraFechamentoCartao.fechamentoDoMes(cartao, alvo.minusMonths(1));
        return repository.findByEspacoIdAndCartaoIdAndFaturaIdIsNullAndDataBetweenOrderByDataDesc(
                        espacoId, cartaoId, fechamentoAnterior.plusDays(1), fechamentoAlvo)
                .stream().map(this::toDTO).toList();
    }

    // Usado pela tela de Lançamentos: itens de fatura ainda em aberto,
    // filtráveis por conta de pagamento do cartão (cartaoId continua
    // aceito para reaproveitar a mesma tela em contextos específicos de
    // um cartão). Sem cartaoId nem contaId, traz de todos os cartões do espaço.
    public List<ItemFaturaDTO> buscarAbertosPorFiltro(String month, Long contaId, Long cartaoId) {
        return buscarAbertosPorFiltro(month, contaId, cartaoId, null);
    }

    public List<ItemFaturaDTO> buscarAbertosPorFiltro(String month, Long contaId, Long cartaoId, Long centroCustoId) {
        Long espacoId = contextoEspaco.espacoAtual();
        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
        return repository.buscarAbertosPorFiltro(espacoId, ym.atDay(1), ym.atEndOfMonth(), contaId, cartaoId)
                .stream()
                .filter(i -> centroCustoId == null || centroCustoId.equals(i.getCentroCustoId()))
                .map(this::toDTO).toList();
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
                // O valor informado é o total da compra — cada parcela recebe uma
                // fração dele, não o valor integral repetido.
                item.setValor(CalculadoraParcelas.valorParcela(dto.getValor(), dto.getTotalParcelas(), i));
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
        item.setCentroCustoId(dto.getCentroCustoId());
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
                .fixa(dto.isFixa())
                .espacoId(espacoId)
                .usuarioId(usuarioId)
                .centroCustoId(dto.getCentroCustoId())
                .build();
    }

    public ItemFaturaDTO toDTO(ItemFatura i) {
        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setId(i.getId());
        dto.setCartaoId(i.getCartao().getId());
        dto.setCartaoNome(i.getCartao().getNome());
        dto.setCartaoCor(i.getCartao().getCor());
        dto.setContaPagamentoId(i.getCartao().getContaPagamento().getId());
        dto.setContaPagamentoNome(i.getCartao().getContaPagamento().getNome());
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
        dto.setFixa(i.isFixa());
        dto.setOrigemFixaId(i.getOrigemFixaId());
        dto.setOrigemRecorrenciaId(i.getOrigemRecorrenciaId());

        if (i.getFatura() != null) {
            Transacao despesa = i.getFatura().getTransacaoDespesa();
            if (despesa.getDataCancelamento() != null) {
                dto.setFaturaStatus("CANCELADA");
            } else if (despesa.getDataPagamento() != null) {
                dto.setFaturaStatus("PAGA");
            } else {
                LocalDate venc = despesa.getDataVencimento() != null ? despesa.getDataVencimento() : despesa.getData();
                dto.setFaturaStatus(venc.isBefore(LocalDate.now()) ? "ATRASADA" : "PENDENTE");
            }
        }

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

        dto.setCentroCustoId(i.getCentroCustoId());
        if (i.getCentroCusto() != null) {
            CentroCustoDTO ccDTO = new CentroCustoDTO();
            ccDTO.setId(i.getCentroCusto().getId());
            ccDTO.setNome(i.getCentroCusto().getNome());
            ccDTO.setCor(i.getCentroCusto().getCor());
            dto.setCentroCusto(ccDTO);
        }

        return dto;
    }
}
