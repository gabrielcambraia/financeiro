package com.financeiro.service;

import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransacaoService {

    private final TransacaoRepository repository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaService contaService;

    public List<TransacaoDTO> findByFilters(String month, Long contaId, TipoTransacao tipo, Long categoriaId) {
        YearMonth ym = YearMonth.parse(month);
        String start = ym.atDay(1).toString();
        String end = ym.atEndOfMonth().toString();
        List<Transacao> raw = contaId != null
                ? repository.findByContaIdAndDataBetweenOrderByDataDesc(contaId, start, end)
                : repository.findByDataBetweenOrderByDataDesc(start, end);
        return raw.stream()
                .filter(t -> tipo == null || t.getTipo() == tipo)
                .filter(t -> categoriaId == null
                        || (t.getCategoria() != null && t.getCategoria().getId().equals(categoriaId)))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<TransacaoDTO> create(TransacaoDTO dto) {
        Conta conta = contaRepository.findById(dto.getContaId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        Categoria categoria = dto.getCategoriaId() != null
                ? categoriaRepository.findById(dto.getCategoriaId()).orElse(null)
                : null;

        List<Transacao> criadas = new ArrayList<>();

        if (dto.getTotalParcelas() != null && dto.getTotalParcelas() > 1) {
            String grupoId = UUID.randomUUID().toString();
            LocalDate dataBase = LocalDate.parse(dto.getData());
            for (int i = 1; i <= dto.getTotalParcelas(); i++) {
                LocalDate dataParcela = dataBase.plusMonths(i - 1);
                boolean dataAlcancada = !dataParcela.isAfter(LocalDate.now());
                Transacao t = buildTransacao(dto, conta, categoria);
                t.setTotalParcelas(dto.getTotalParcelas());
                t.setNumeroParcela(i);
                t.setGrupoParcelaId(grupoId);
                t.setData(dataParcela.toString());
                t.setFixa(false);
                t.setSaldoAjustado(dataAlcancada);
                criadas.add(repository.save(t));
                if (dataAlcancada) {
                    contaService.adjustBalance(conta, computeDelta(dto.getTipo(), dto.getValor()));
                }
            }
            return criadas.stream().map(this::toDTO).toList();
        } else {
            boolean dataAlcancada = !LocalDate.parse(dto.getData()).isAfter(LocalDate.now());
            Transacao t = buildTransacao(dto, conta, categoria);
            t.setSaldoAjustado(dataAlcancada);
            criadas.add(repository.save(t));

            if (dto.isFixa()) {
                LocalDate dataBase = LocalDate.parse(dto.getData());
                for (int i = 1; i <= 11; i++) {
                    Transacao futura = buildTransacao(dto, conta, categoria);
                    futura.setData(dataBase.plusMonths(i).toString());
                    futura.setSaldoAjustado(false);
                    repository.save(futura);
                }
            }

            if (dataAlcancada) {
                contaService.adjustBalance(conta, computeDelta(dto.getTipo(), dto.getValor()));
            }
            return criadas.stream().map(this::toDTO).toList();
        }
    }

    @Transactional
    public TransacaoDTO update(Long id, TransacaoDTO dto) {
        Transacao existente = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        Conta novaConta = contaRepository.findById(dto.getContaId())
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        Categoria novaCategoria = dto.getCategoriaId() != null
                ? categoriaRepository.findById(dto.getCategoriaId()).orElse(null)
                : null;

        boolean estavaAjustada = existente.isSaldoAjustado();
        boolean novaDataAlcancada = !LocalDate.parse(dto.getData()).isAfter(LocalDate.now());

        // Reverte o saldo antigo apenas se ele foi aplicado
        if (estavaAjustada) {
            contaService.adjustBalance(existente.getConta(),
                    computeDelta(existente.getTipo(), existente.getValor()).negate());
        }

        existente.setConta(novaConta);
        existente.setCategoria(novaCategoria);
        existente.setTipo(dto.getTipo());
        existente.setTipoPagamento(dto.getTipoPagamento());
        existente.setValor(dto.getValor());
        existente.setDescricao(dto.getDescricao());
        existente.setData(dto.getData());
        existente.setFixa(dto.isFixa());
        existente.setSaldoAjustado(novaDataAlcancada);
        repository.save(existente);

        // Aplica o novo saldo apenas se a nova data já chegou
        if (novaDataAlcancada) {
            contaService.adjustBalance(novaConta, computeDelta(dto.getTipo(), dto.getValor()));
        }

        return toDTO(existente);
    }

    @Transactional
    public void delete(Long id, String scope) {
        Transacao t = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));

        if ("GRUPO".equals(scope) && t.getGrupoParcelaId() != null) {
            List<Transacao> grupo = repository.findByGrupoParcelaId(t.getGrupoParcelaId());
            grupo.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                    contaService.adjustBalance(tx.getConta(), computeDelta(tx.getTipo(), tx.getValor()).negate()));
            repository.deleteAll(grupo);
        } else if ("FUTURAS".equals(scope)) {
            if (t.getGrupoParcelaId() != null) {
                List<Transacao> futuras = repository.findByGrupoParcelaIdAndDataGreaterThanEqual(
                        t.getGrupoParcelaId(), t.getData());
                futuras.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                        contaService.adjustBalance(tx.getConta(), computeDelta(tx.getTipo(), tx.getValor()).negate()));
                repository.deleteAll(futuras);
            } else if (t.isFixa()) {
                List<Transacao> futuras = repository.findByFixaTrueAndDataGreaterThanEqual(t.getData());
                futuras.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                        contaService.adjustBalance(tx.getConta(), computeDelta(tx.getTipo(), tx.getValor()).negate()));
                repository.deleteAll(futuras);
            } else {
                if (t.isSaldoAjustado()) {
                    contaService.adjustBalance(t.getConta(), computeDelta(t.getTipo(), t.getValor()).negate());
                }
                repository.delete(t);
            }
        } else {
            if (t.isSaldoAjustado()) {
                contaService.adjustBalance(t.getConta(), computeDelta(t.getTipo(), t.getValor()).negate());
            }
            repository.delete(t);
        }
    }

    private Transacao buildTransacao(TransacaoDTO dto, Conta conta, Categoria categoria) {
        return Transacao.builder()
                .conta(conta)
                .categoria(categoria)
                .tipo(dto.getTipo())
                .tipoPagamento(dto.getTipoPagamento())
                .valor(dto.getValor())
                .descricao(dto.getDescricao())
                .data(dto.getData())
                .fixa(dto.isFixa())
                .build();
    }

    private BigDecimal computeDelta(TipoTransacao tipo, BigDecimal valor) {
        return tipo == TipoTransacao.RECEITA ? valor : valor.negate();
    }

    public TransacaoDTO toDTO(Transacao t) {
        TransacaoDTO dto = new TransacaoDTO();
        dto.setId(t.getId());
        dto.setContaId(t.getConta().getId());
        dto.setTipo(t.getTipo());
        dto.setTipoPagamento(t.getTipoPagamento());
        dto.setValor(t.getValor());
        dto.setDescricao(t.getDescricao());
        dto.setData(t.getData());
        dto.setFixa(t.isFixa());
        dto.setTotalParcelas(t.getTotalParcelas());
        dto.setNumeroParcela(t.getNumeroParcela());
        dto.setGrupoParcelaId(t.getGrupoParcelaId());

        ContaDTO contaDTO = new ContaDTO();
        contaDTO.setId(t.getConta().getId());
        contaDTO.setNome(t.getConta().getNome());
        contaDTO.setTipo(t.getConta().getTipo());
        contaDTO.setSaldo(t.getConta().getSaldo());
        contaDTO.setCor(t.getConta().getCor());
        contaDTO.setIcone(t.getConta().getIcone());
        dto.setConta(contaDTO);

        if (t.getCategoria() != null) {
            CategoriaDTO catDTO = new CategoriaDTO();
            catDTO.setId(t.getCategoria().getId());
            catDTO.setNome(t.getCategoria().getNome());
            catDTO.setTipo(t.getCategoria().getTipo());
            catDTO.setCor(t.getCategoria().getCor());
            catDTO.setIcone(t.getCategoria().getIcone());
            dto.setCategoria(catDTO);
            dto.setCategoriaId(t.getCategoria().getId());
        }

        return dto;
    }
}
