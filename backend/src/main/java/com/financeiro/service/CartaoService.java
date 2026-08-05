package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.CartaoDTO;
import com.financeiro.entity.Banco;
import com.financeiro.entity.Cartao;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Fatura;
import com.financeiro.entity.ItemFatura;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.BancoRepository;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.FaturaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartaoService {

    private final CartaoRepository repository;
    private final ContaRepository contaRepository;
    private final BancoRepository bancoRepository;
    private final BancoService bancoService;
    private final ItemFaturaRepository itemFaturaRepository;
    private final FaturaRepository faturaRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoEntidade contextoEntidade;
    private final ContaService contaService;

    public List<CartaoDTO> findAll() {
        Long espacoId = contextoEspaco.espacoAtual();
        Long entidadeId = contextoEntidade.entidadeAtual();
        List<Cartao> cartoes = entidadeId != null
                ? repository.findByEspacoIdFiltradoPorEntidade(espacoId, entidadeId)
                : repository.findByEspacoId(espacoId);
        return cartoes.stream().map(this::toDTO).toList();
    }

    public CartaoDTO create(CartaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Conta contaPagamento = contaRepository.findByIdAndEspacoId(dto.getContaPagamentoId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta de pagamento não encontrada"));

        Cartao cartao = Cartao.builder()
                .nome(dto.getNome())
                .limite(dto.getLimite())
                .diaFechamento(dto.getDiaFechamento())
                .diaVencimento(dto.getDiaVencimento())
                .contaPagamento(contaPagamento)
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .banco(resolverBanco(dto.getBancoId()))
                .espacoId(espacoId)
                .entidadeId(dto.getEntidadeId())
                .build();
        return toDTO(repository.save(cartao));
    }

    public CartaoDTO update(Long id, CartaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Cartao cartao = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Cartão não encontrado: " + id));
        Conta contaPagamento = contaRepository.findByIdAndEspacoId(dto.getContaPagamentoId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta de pagamento não encontrada"));

        cartao.setNome(dto.getNome());
        cartao.setLimite(dto.getLimite());
        cartao.setDiaFechamento(dto.getDiaFechamento());
        cartao.setDiaVencimento(dto.getDiaVencimento());
        cartao.setContaPagamento(contaPagamento);
        cartao.setCor(dto.getCor());
        cartao.setIcone(dto.getIcone());
        cartao.setBanco(resolverBanco(dto.getBancoId()));
        return toDTO(repository.save(cartao));
    }

    public void delete(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Cartao cartao = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Cartão não encontrado: " + id));
        repository.delete(cartao);
    }

    private Banco resolverBanco(Long bancoId) {
        return bancoId != null ? bancoRepository.findById(bancoId).orElse(null) : null;
    }

    public CartaoDTO toDTO(Cartao c) {
        Long espacoId = c.getEspacoId();

        BigDecimal faturaAtualTotal = itemFaturaRepository
                .findByEspacoIdAndCartaoIdAndFaturaIdIsNullOrderByDataDesc(espacoId, c.getId()).stream()
                .filter(i -> i.getDataCancelamento() == null)
                .map(ItemFatura::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal faturasFechadasNaoPagas = faturaRepository
                .findByEspacoIdAndCartaoIdOrderByDataFechamentoDesc(espacoId, c.getId()).stream()
                .map(Fatura::getTransacaoDespesa)
                .filter(despesa -> !despesa.isSaldoAjustado() && despesa.getDataCancelamento() == null)
                .map(com.financeiro.entity.Transacao::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal comprometido = faturaAtualTotal.add(faturasFechadasNaoPagas);

        CartaoDTO dto = new CartaoDTO();
        dto.setId(c.getId());
        dto.setNome(c.getNome());
        dto.setLimite(c.getLimite());
        dto.setDiaFechamento(c.getDiaFechamento());
        dto.setDiaVencimento(c.getDiaVencimento());
        dto.setContaPagamentoId(c.getContaPagamento().getId());
        dto.setContaPagamento(contaService.toDTO(c.getContaPagamento()));
        dto.setCor(c.getCor());
        dto.setIcone(c.getIcone());
        if (c.getBanco() != null) {
            dto.setBancoId(c.getBanco().getId());
            dto.setBanco(bancoService.toDTO(c.getBanco()));
        }
        dto.setFaturaAtualTotal(faturaAtualTotal);
        dto.setLimiteDisponivel(c.getLimite().subtract(comprometido));
        dto.setEntidadeId(c.getEntidadeId());
        return dto;
    }
}
