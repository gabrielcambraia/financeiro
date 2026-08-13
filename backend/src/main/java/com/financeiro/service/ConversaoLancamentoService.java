package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.ConversaoParaCartaoDTO;
import com.financeiro.dto.ConversaoParaDebitoDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.Cartao;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Fatura;
import com.financeiro.entity.ItemFatura;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.EscopoAtualizacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.DividaRepository;
import com.financeiro.repository.FaturaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.MovimentacaoAtivoRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversaoLancamentoService {

    private final TransacaoRepository transacaoRepository;
    private final ItemFaturaRepository itemFaturaRepository;
    private final FaturaRepository faturaRepository;
    private final CartaoRepository cartaoRepository;
    private final ContaRepository contaRepository;
    private final MovimentacaoAtivoRepository movimentacaoAtivoRepository;
    private final DividaRepository dividaRepository;
    private final ContaService contaService;
    private final ResolvedorFaturaAlvo resolvedorFaturaAlvo;
    private final ItemFaturaService itemFaturaService;
    private final TransacaoService transacaoService;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;

    // -----------------------------------------------------------------------
    // Débito → Crédito
    // -----------------------------------------------------------------------

    @Transactional
    public List<ItemFaturaDTO> converterParaCredito(Long transacaoId, ConversaoParaCartaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioId = contextoUsuario.usuarioAtual();

        Transacao t = transacaoRepository.findByIdAndEspacoId(transacaoId, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        validarOrigemDebito(t, espacoId);

        Cartao cartao = cartaoRepository.findByIdAndEspacoId(dto.getCartaoId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Cartão não encontrado"));
        if (cartao.getContaPagamento() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cartão sem conta de pagamento configurada");
        }

        EscopoAtualizacao escopo = dto.getEscopo() != null ? dto.getEscopo() : EscopoAtualizacao.UNICA;
        boolean isFixaFuturas = t.isFixa() && escopo == EscopoAtualizacao.FUTURAS && t.getOrigemFixaId() != null;
        // Parcelas incompatíveis com fixa FUTURAS
        Integer parcelas = isFixaFuturas ? null : dto.getTotalParcelas();

        record LinhaMes(Transacao row, LocalDate data, BigDecimal valor) {}
        List<LinhaMes> linhas = new ArrayList<>();

        if (isFixaFuturas) {
            List<Transacao> futuras = transacaoRepository.findByEspacoIdAndOrigemFixaIdAndDataGreaterThanEqual(
                    espacoId, t.getOrigemFixaId(), t.getData());
            bloquearFixaSePagaOuCancelada(futuras, t.getData());
            for (Transacao f : futuras) {
                linhas.add(new LinhaMes(f, f.getData(), f.getValor()));
            }
        } else if (parcelas != null && parcelas > 1) {
            LocalDate dataBase = t.getData();
            for (int i = 1; i <= parcelas; i++) {
                linhas.add(new LinhaMes(t, dataBase.plusMonths(i - 1),
                        CalculadoraParcelas.valorParcela(t.getValor(), parcelas, i)));
            }
        } else {
            linhas.add(new LinhaMes(t, t.getData(), t.getValor()));
        }

        // Pré-valida todas as faturas alvo antes de qualquer escrita
        List<Fatura> faturasAlvo = new ArrayList<>();
        for (LinhaMes l : linhas) {
            Fatura faturaAlvo = resolvedorFaturaAlvo.resolver(cartao, l.data());
            if (faturaAlvo != null && faturaAlvo.getTransacaoDespesa().getDataPagamento() != null) {
                YearMonth mes = YearMonth.from(faturaAlvo.getDataFechamento());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Fatura referente ao novo crédito já paga (" +
                        mes.format(DateTimeFormatter.ofPattern("MM/yyyy")) +
                        "). Não é possível converter para uma fatura já quitada.");
            }
            faturasAlvo.add(faturaAlvo);
        }

        // Reverte saldo das rows de origem (usa Set para não reverter duas vezes no caso de parcelas)
        Set<Long> jaRevertidos = new HashSet<>();
        for (LinhaMes l : linhas) {
            Transacao row = l.row();
            if (!jaRevertidos.contains(row.getId()) && row.isSaldoAjustado()) {
                contaService.adjustBalance(row.getConta(), deltaDespesa(row).negate());
                jaRevertidos.add(row.getId());
            }
        }

        // Apaga rows de origem
        Long origemAntiga = t.getOrigemFixaId();
        if (isFixaFuturas) {
            List<Transacao> unicasRows = linhas.stream().map(LinhaMes::row).distinct().toList();
            transacaoRepository.deleteAll(unicasRows);
            transacaoRepository.encerrarTrilhoAntigo(espacoId, origemAntiga, t.getData());
        } else {
            transacaoRepository.delete(t);
        }

        // Cria ItemFatura rows
        List<ItemFatura> criados = new ArrayList<>();
        Long cabecaId = null;
        String grupoParcelaId = (parcelas != null && parcelas > 1) ? UUID.randomUUID().toString() : null;

        for (int i = 0; i < linhas.size(); i++) {
            LinhaMes l = linhas.get(i);
            Fatura faturaAlvo = faturasAlvo.get(i);

            ItemFatura item = ItemFatura.builder()
                    .cartao(cartao)
                    .categoria(t.getCategoria())
                    .valor(l.valor())
                    .descricao(t.getDescricao())
                    .data(l.data())
                    .fatura(faturaAlvo)
                    .fixa(isFixaFuturas)
                    .serieAtiva(true)
                    .grupoParcelaId(grupoParcelaId)
                    .numeroParcela(grupoParcelaId != null ? i + 1 : null)
                    .totalParcelas(grupoParcelaId != null ? parcelas : null)
                    .espacoId(espacoId)
                    .usuarioId(usuarioId)
                    .build();

            item = itemFaturaRepository.save(item);

            if (isFixaFuturas) {
                if (cabecaId == null) {
                    item.setOrigemFixaId(item.getId());
                    cabecaId = item.getId();
                } else {
                    item.setOrigemFixaId(cabecaId);
                }
                item = itemFaturaRepository.save(item);
            }

            if (faturaAlvo != null) {
                reconciliarFaturaFechadaAdicao(faturaAlvo, item.getValor());
            }

            criados.add(item);
        }

        return criados.stream().map(itemFaturaService::toDTO).toList();
    }

    // -----------------------------------------------------------------------
    // Crédito → Débito
    // -----------------------------------------------------------------------

    @Transactional
    public List<TransacaoDTO> converterParaDebito(Long itemId, ConversaoParaDebitoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioId = contextoUsuario.usuarioAtual();

        ItemFatura item = itemFaturaRepository.findByIdAndEspacoId(itemId, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Item de fatura não encontrado"));

        validarOrigemCredito(item);

        EscopoAtualizacao escopo = dto.getEscopo() != null ? dto.getEscopo() : EscopoAtualizacao.UNICA;
        boolean isFixaFuturas = item.isFixa() && escopo == EscopoAtualizacao.FUTURAS && item.getOrigemFixaId() != null;

        List<ItemFatura> itens = new ArrayList<>();
        if (isFixaFuturas) {
            itens = itemFaturaRepository.findByEspacoIdAndOrigemFixaIdAndDataGreaterThanEqual(
                    espacoId, item.getOrigemFixaId(), item.getData());
            bloquearItemSeFaturaPagaOuCancelada(itens, item.getData());
        } else {
            itens = List.of(item);
        }

        Conta contaDestino = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));

        boolean quitarNaCriacao = Boolean.TRUE.equals(dto.getQuitarNaCriacao());
        LocalDate dataPagamento = quitarNaCriacao
                ? (dto.getDataPagamento() != null ? dto.getDataPagamento() : LocalDate.now())
                : null;

        // Desanexa itens de faturas fechadas e reconcilia valor
        for (ItemFatura i : itens) {
            if (i.getFatura() != null) {
                reconciliarFaturaFechadaRemocao(i.getFatura(), i.getValor());
                i.setFatura(null);
                itemFaturaRepository.save(i);
            }
        }

        // Apaga itens de origem
        Long origemAntiga = item.getOrigemFixaId();
        itemFaturaRepository.deleteAll(itens);
        if (isFixaFuturas) {
            itemFaturaRepository.encerrarTrilhoAntigo(espacoId, origemAntiga, item.getData());
        }

        // Cria Transacao rows
        List<Transacao> criadas = new ArrayList<>();
        Long cabecaId = null;

        for (ItemFatura i : itens) {
            boolean paga = quitarNaCriacao && !i.getData().isAfter(LocalDate.now());

            Transacao novaT = Transacao.builder()
                    .conta(contaDestino)
                    .categoria(i.getCategoria())
                    .tipo(TipoTransacao.DESPESA)
                    .tipoPagamento(TipoPagamento.DEBITO)
                    .valor(i.getValor())
                    .descricao(i.getDescricao())
                    .data(i.getData())
                    .dataVencimento(i.getData())
                    .fixa(isFixaFuturas)
                    .saldoAjustado(paga)
                    .dataPagamento(paga ? dataPagamento : null)
                    .espacoId(espacoId)
                    .usuarioId(usuarioId)
                    .build();

            novaT = transacaoRepository.save(novaT);

            if (isFixaFuturas) {
                if (cabecaId == null) {
                    novaT.setOrigemFixaId(novaT.getId());
                    novaT.setSerieAtiva(true);
                    cabecaId = novaT.getId();
                } else {
                    novaT.setOrigemFixaId(cabecaId);
                    novaT.setSerieAtiva(true);
                }
                novaT = transacaoRepository.save(novaT);
            }

            if (paga) {
                contaService.adjustBalance(contaDestino, i.getValor().negate());
            }

            criadas.add(novaT);
        }

        return criadas.stream().map(transacaoService::toDTO).toList();
    }

    // -----------------------------------------------------------------------
    // Validações
    // -----------------------------------------------------------------------

    private void validarOrigemDebito(Transacao t, Long espacoId) {
        if (t.getTipo() != TipoTransacao.DESPESA || t.getTipoPagamento() != TipoPagamento.DEBITO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Apenas despesas em débito podem ser convertidas para cartão de crédito");
        }
        if (t.getDataCancelamento() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível converter uma transação cancelada");
        }
        if (t.getMeta() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação é derivada de uma meta e não pode ser convertida");
        }
        if (movimentacaoAtivoRepository.findByTransacaoId(t.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação é derivada de um investimento e não pode ser convertida");
        }
        if (t.getGrupoParcelaId() != null &&
                dividaRepository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação é derivada de uma dívida e não pode ser convertida");
        }
        faturaRepository.findByTransacaoDespesaId(t.getId()).ifPresent(f -> {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação corresponde ao pagamento da fatura do cartão " +
                    f.getCartao().getNome() + ". Para alterar, acesse a fatura.");
        });
        if (t.getGrupoParcelaId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível converter uma parcela isolada. Converta a compra original.");
        }
    }

    private void validarOrigemCredito(ItemFatura item) {
        if (item.getDataCancelamento() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível converter um item cancelado");
        }
        if (item.getGrupoParcelaId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível converter uma parcela isolada. Converta a compra original.");
        }
        if (item.getFatura() != null) {
            Transacao despesa = item.getFatura().getTransacaoDespesa();
            if (despesa.getDataPagamento() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Item pertence a uma fatura já paga. Não é possível converter para débito.");
            }
            if (despesa.getDataCancelamento() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Item pertence a uma fatura cancelada. Não é possível converter para débito.");
            }
        }
    }

    private void bloquearFixaSePagaOuCancelada(List<Transacao> transacoes, LocalDate dataInicio) {
        boolean temPagaOuCancelada = transacoes.stream()
                .filter(t -> t.getData().isAfter(dataInicio))
                .anyMatch(t -> t.getDataPagamento() != null || t.getDataCancelamento() != null);
        if (temPagaOuCancelada) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível converter: há lançamentos pagos ou cancelados no escopo selecionado.");
        }
    }

    private void bloquearItemSeFaturaPagaOuCancelada(List<ItemFatura> itens, LocalDate dataInicio) {
        boolean temFaturaPagaOuCancelada = itens.stream()
                .filter(i -> i.getData().isAfter(dataInicio))
                .anyMatch(i -> {
                    if (i.getFatura() == null) return false;
                    Transacao despesa = i.getFatura().getTransacaoDespesa();
                    return despesa.getDataPagamento() != null || despesa.getDataCancelamento() != null;
                });
        if (temFaturaPagaOuCancelada) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível converter: há itens em faturas pagas ou canceladas no escopo selecionado.");
        }
    }

    // -----------------------------------------------------------------------
    // Reconciliação de faturas fechadas
    // -----------------------------------------------------------------------

    public void reconciliarFaturaFechadaAdicao(Fatura fatura, BigDecimal valorAdicional) {
        Transacao despesa = fatura.getTransacaoDespesa();
        boolean eraAjustado = despesa.isSaldoAjustado();
        if (eraAjustado) {
            contaService.adjustBalance(despesa.getConta(), despesa.getValor()); // reverte
        }
        despesa.setValor(despesa.getValor().add(valorAdicional));
        transacaoRepository.save(despesa);
        if (eraAjustado) {
            contaService.adjustBalance(despesa.getConta(), despesa.getValor().negate()); // reaplicar
        }
    }

    private void reconciliarFaturaFechadaRemocao(Fatura fatura, BigDecimal valorRemovido) {
        Transacao despesa = fatura.getTransacaoDespesa();
        boolean eraAjustado = despesa.isSaldoAjustado();
        if (eraAjustado) {
            contaService.adjustBalance(despesa.getConta(), despesa.getValor()); // reverte
        }
        BigDecimal novoValor = despesa.getValor().subtract(valorRemovido);
        despesa.setValor(novoValor.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : novoValor);
        transacaoRepository.save(despesa);
        if (eraAjustado) {
            contaService.adjustBalance(despesa.getConta(), despesa.getValor().negate()); // reaplicar
        }
        if (despesa.getValor().compareTo(BigDecimal.ZERO) == 0) {
            log.info("Fatura {} ficou com valor zero após remoção de item. Cancele-a manualmente se necessário.",
                    fatura.getId());
        }
    }

    private BigDecimal deltaDespesa(Transacao t) {
        BigDecimal valorEfetivo = t.getValor().add(t.getMulta() != null ? t.getMulta() : BigDecimal.ZERO);
        return valorEfetivo.negate();
    }
}
