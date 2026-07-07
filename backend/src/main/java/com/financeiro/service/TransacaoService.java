package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
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
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;

    public List<TransacaoDTO> findByFilters(String month, Long contaId, TipoTransacao tipo, Long categoriaId) {
        Long espacoId = contextoEspaco.espacoAtual();
        YearMonth ym = YearMonth.parse(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<Transacao> raw = contaId != null
                ? repository.findByEspacoIdAndContaIdAndDataBetweenOrderByDataDesc(espacoId, contaId, start, end)
                : repository.findByEspacoIdAndDataBetweenOrderByDataDesc(espacoId, start, end);
        return raw.stream()
                .filter(t -> tipo == null || t.getTipo() == tipo)
                .filter(t -> categoriaId == null
                        || (t.getCategoria() != null && t.getCategoria().getId().equals(categoriaId)))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<TransacaoDTO> create(TransacaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioId = contextoUsuario.usuarioAtual();
        Conta conta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        Categoria categoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        List<Transacao> criadas = new ArrayList<>();

        if (dto.getTotalParcelas() != null && dto.getTotalParcelas() > 1) {
            String grupoId = UUID.randomUUID().toString();
            LocalDate dataBase = dto.getData();
            for (int i = 1; i <= dto.getTotalParcelas(); i++) {
                LocalDate dataParcela = dataBase.plusMonths(i - 1);
                boolean dataAlcancada = !dataParcela.isAfter(LocalDate.now());
                Transacao t = buildTransacao(dto, conta, categoria, espacoId, usuarioId);
                t.setTotalParcelas(dto.getTotalParcelas());
                t.setNumeroParcela(i);
                t.setGrupoParcelaId(grupoId);
                t.setData(dataParcela);
                t.setFixa(false);
                t.setSaldoAjustado(dataAlcancada);
                criadas.add(repository.save(t));
                if (dataAlcancada) {
                    contaService.adjustBalance(conta, computeDelta(dto.getTipo(), dto.getValor()));
                }
            }
            return criadas.stream().map(this::toDTO).toList();
        } else {
            boolean dataAlcancada = !dto.getData().isAfter(LocalDate.now());
            Transacao t = buildTransacao(dto, conta, categoria, espacoId, usuarioId);
            t.setSaldoAjustado(dataAlcancada);
            criadas.add(repository.save(t));

            if (dto.isFixa()) {
                LocalDate dataBase = dto.getData();
                for (int i = 1; i <= 11; i++) {
                    Transacao futura = buildTransacao(dto, conta, categoria, espacoId, usuarioId);
                    futura.setData(dataBase.plusMonths(i));
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
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao existente = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));
        Conta novaConta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new RuntimeException("Conta não encontrada"));
        Categoria novaCategoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        boolean estavaAjustada = existente.isSaldoAjustado();
        boolean novaDataAlcancada = !dto.getData().isAfter(LocalDate.now());

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
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new RuntimeException("Transação não encontrada"));

        if ("GRUPO".equals(scope) && t.getGrupoParcelaId() != null) {
            List<Transacao> grupo = repository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId());
            grupo.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                    contaService.adjustBalance(tx.getConta(), computeDelta(tx.getTipo(), tx.getValor()).negate()));
            repository.deleteAll(grupo);
        } else if ("FUTURAS".equals(scope)) {
            if (t.getGrupoParcelaId() != null) {
                List<Transacao> futuras = repository.findByEspacoIdAndGrupoParcelaIdAndDataGreaterThanEqual(
                        espacoId, t.getGrupoParcelaId(), t.getData());
                futuras.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                        contaService.adjustBalance(tx.getConta(), computeDelta(tx.getTipo(), tx.getValor()).negate()));
                repository.deleteAll(futuras);
            } else if (t.isFixa()) {
                List<Transacao> futuras = repository.findByEspacoIdAndFixaTrueAndDataGreaterThanEqual(espacoId, t.getData());
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

    private Transacao buildTransacao(TransacaoDTO dto, Conta conta, Categoria categoria, Long espacoId, Long usuarioId) {
        return Transacao.builder()
                .conta(conta)
                .categoria(categoria)
                .tipo(dto.getTipo())
                .tipoPagamento(dto.getTipoPagamento())
                .valor(dto.getValor())
                .descricao(dto.getDescricao())
                .data(dto.getData())
                .fixa(dto.isFixa())
                .espacoId(espacoId)
                .usuarioId(usuarioId)
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
        dto.setUsuarioId(t.getUsuarioId());

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
