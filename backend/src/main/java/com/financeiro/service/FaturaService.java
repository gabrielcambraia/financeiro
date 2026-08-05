package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.FaturaDTO;
import com.financeiro.entity.Fatura;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.FaturaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaturaService {

    private final FaturaRepository repository;
    private final ItemFaturaRepository itemFaturaRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoEntidade contextoEntidade;
    private final CartaoService cartaoService;
    private final TransacaoService transacaoService;
    private final ItemFaturaService itemFaturaService;

    public List<FaturaDTO> findByCartao(Long cartaoId) {
        return findByCartao(cartaoId, null);
    }

    // month presente filtra pelo mês de fechamento — permite navegar o
    // histórico de faturas de um cartão mês a mês em vez de trazer tudo de uma vez.
    public List<FaturaDTO> findByCartao(Long cartaoId, String month) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long entidadeId = contextoEntidade.entidadeAtual();
        List<Fatura> faturas;
        if (month != null) {
            YearMonth ym = YearMonth.parse(month);
            LocalDate inicio = ym.atDay(1);
            LocalDate fim = ym.atEndOfMonth();
            faturas = repository.findByEspacoIdAndCartaoIdAndDataFechamentoBetweenOrderByDataFechamentoDesc(
                    espacoId, cartaoId, inicio, fim);
        } else if (entidadeId != null) {
            faturas = repository.findByEspacoIdAndCartaoIdFiltradoPorEntidade(espacoId, cartaoId, entidadeId);
        } else {
            faturas = repository.findByEspacoIdAndCartaoIdOrderByDataFechamentoDesc(espacoId, cartaoId);
        }
        return faturas.stream().map(f -> toDTO(f, false)).toList();
    }

    public FaturaDTO findById(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Fatura fatura = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Fatura não encontrada"));
        return toDTO(fatura, true);
    }

    private FaturaDTO toDTO(Fatura f, boolean comItens) {
        Long espacoId = contextoEspaco.espacoAtual();
        FaturaDTO dto = new FaturaDTO();
        dto.setId(f.getId());
        dto.setCartaoId(f.getCartao().getId());
        dto.setCartao(cartaoService.toDTO(f.getCartao()));
        dto.setDataFechamento(f.getDataFechamento());
        dto.setDataVencimento(f.getDataVencimento());
        dto.setTransacaoDespesaId(f.getTransacaoDespesa().getId());
        dto.setValor(f.getTransacaoDespesa().getValor());
        dto.setStatus(transacaoService.toDTO(f.getTransacaoDespesa()).getStatus());

        if (comItens) {
            dto.setItens(itemFaturaRepository.findByEspacoIdAndFaturaIdOrderByDataDesc(espacoId, f.getId())
                    .stream().map(itemFaturaService::toDTO).toList());
        }

        dto.setEntidadeId(f.getEntidadeId());
        return dto;
    }
}
