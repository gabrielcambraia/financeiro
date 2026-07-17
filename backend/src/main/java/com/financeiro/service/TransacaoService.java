package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.StatusTransacao;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        Categoria categoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        // Quitação é manual: uma transação só afeta o saldo quando alguém a marca
        // como paga (PATCH /pagar). Na criação, "quitarNaCriacao" (default true)
        // decide se ela já nasce paga — só faz sentido para datas <= hoje; datas
        // futuras nascem sempre PENDENTES, mesmo que o campo venha true.
        boolean quitarNaCriacao = dto.getQuitarNaCriacao() == null || dto.getQuitarNaCriacao();

        List<Transacao> criadas = new ArrayList<>();

        if (dto.getTotalParcelas() != null && dto.getTotalParcelas() > 1) {
            String grupoId = UUID.randomUUID().toString();
            LocalDate dataBase = dto.getData();
            for (int i = 1; i <= dto.getTotalParcelas(); i++) {
                LocalDate dataParcela = dataBase.plusMonths(i - 1);
                boolean paga = quitarNaCriacao && !dataParcela.isAfter(LocalDate.now());
                Transacao t = buildTransacao(dto, conta, categoria, espacoId, usuarioId);
                t.setTotalParcelas(dto.getTotalParcelas());
                t.setNumeroParcela(i);
                t.setGrupoParcelaId(grupoId);
                t.setData(dataParcela);
                t.setDataVencimento(dataParcela);
                t.setFixa(false);
                t.setSaldoAjustado(paga);
                t.setDataPagamento(paga ? dataParcela : null);
                criadas.add(repository.save(t));
                if (paga) {
                    contaService.adjustBalance(conta, computeDelta(dto.getTipo(), dto.getValor()));
                }
            }
            return criadas.stream().map(this::toDTO).toList();
        } else {
            boolean paga = quitarNaCriacao && !dto.getData().isAfter(LocalDate.now());
            Transacao t = buildTransacao(dto, conta, categoria, espacoId, usuarioId);
            t.setSaldoAjustado(paga);
            t.setDataPagamento(paga ? dto.getData() : null);
            criadas.add(repository.save(t));

            if (dto.isFixa()) {
                LocalDate dataBase = dto.getData();
                for (int i = 1; i <= 11; i++) {
                    LocalDate dataFutura = dataBase.plusMonths(i);
                    Transacao futura = buildTransacao(dto, conta, categoria, espacoId, usuarioId);
                    futura.setData(dataFutura);
                    futura.setDataVencimento(dataFutura);
                    futura.setSaldoAjustado(false);
                    futura.setDataPagamento(null);
                    repository.save(futura);
                }
            }

            if (paga) {
                contaService.adjustBalance(conta, computeDelta(dto.getTipo(), dto.getValor()));
            }
            return criadas.stream().map(this::toDTO).toList();
        }
    }

    @Transactional
    public TransacaoDTO update(Long id, TransacaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao existente = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));
        Conta novaConta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        Categoria novaCategoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        if (dto.getDataPagamento() != null) {
            validarDataPagamento(dto.getDataPagamento());
        }

        boolean estavaAjustada = existente.isSaldoAjustado();
        // A quitação é guiada pela data de pagamento informada, não mais por
        // "data <= hoje" — quitação é manual (ver create()).
        boolean novaPaga = dto.getDataPagamento() != null && existente.getDataCancelamento() == null;

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
        existente.setDataVencimento(dto.getDataVencimento() != null ? dto.getDataVencimento() : dto.getData());
        existente.setDataPagamento(novaPaga ? dto.getDataPagamento() : null);
        existente.setFixa(dto.isFixa());
        existente.setSaldoAjustado(novaPaga);
        repository.save(existente);

        // Aplica o novo saldo apenas se a transação está paga
        if (novaPaga) {
            contaService.adjustBalance(novaConta, computeDelta(dto.getTipo(), dto.getValor()));
        }

        return toDTO(existente);
    }

    @Transactional
    public void delete(Long id, String scope) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

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

    @Transactional
    public TransacaoDTO pagar(Long id, LocalDate dataPagamento) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        if (t.getDataCancelamento() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível pagar uma transação cancelada");
        }

        LocalDate dataPg = dataPagamento != null ? dataPagamento : LocalDate.now();
        validarDataPagamento(dataPg);
        if (!t.isSaldoAjustado()) {
            contaService.adjustBalance(t.getConta(), computeDelta(t.getTipo(), t.getValor()));
            t.setSaldoAjustado(true);
        }
        t.setDataPagamento(dataPg);
        repository.save(t);
        return toDTO(t);
    }

    @Transactional
    public TransacaoDTO estornar(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        if (t.isSaldoAjustado()) {
            contaService.adjustBalance(t.getConta(), computeDelta(t.getTipo(), t.getValor()).negate());
            t.setSaldoAjustado(false);
        }
        t.setDataPagamento(null);
        repository.save(t);
        return toDTO(t);
    }

    @Transactional
    public TransacaoDTO cancelar(Long id, String scope) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        if ("GRUPO".equals(scope) && t.getGrupoParcelaId() != null) {
            repository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId())
                    .forEach(this::cancelarTransacao);
        } else if ("FUTURAS".equals(scope)) {
            if (t.getGrupoParcelaId() != null) {
                repository.findByEspacoIdAndGrupoParcelaIdAndDataGreaterThanEqual(
                        espacoId, t.getGrupoParcelaId(), t.getData()).forEach(this::cancelarTransacao);
            } else if (t.isFixa()) {
                repository.findByEspacoIdAndFixaTrueAndDataGreaterThanEqual(espacoId, t.getData())
                        .forEach(this::cancelarTransacao);
            } else {
                cancelarTransacao(t);
            }
        } else {
            cancelarTransacao(t);
        }

        return toDTO(repository.findByIdAndEspacoId(id, espacoId).orElseThrow());
    }

    private void validarDataPagamento(LocalDate dataPagamento) {
        if (dataPagamento.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Data de pagamento não pode ser no futuro");
        }
    }

    private void cancelarTransacao(Transacao t) {
        if (t.getDataCancelamento() != null) {
            return; // já cancelada, idempotente
        }
        if (t.isSaldoAjustado()) {
            contaService.adjustBalance(t.getConta(), computeDelta(t.getTipo(), t.getValor()).negate());
            t.setSaldoAjustado(false);
        }
        t.setDataCancelamento(LocalDate.now());
        repository.save(t);
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
                .dataVencimento(dto.getDataVencimento() != null ? dto.getDataVencimento() : dto.getData())
                .fixa(dto.isFixa())
                .espacoId(espacoId)
                .usuarioId(usuarioId)
                .build();
    }

    private StatusTransacao computeStatus(Transacao t) {
        if (t.getDataCancelamento() != null) {
            return StatusTransacao.CANCELADA;
        }
        if (t.getDataPagamento() != null) {
            return StatusTransacao.PAGA;
        }
        LocalDate vencimento = t.getDataVencimento() != null ? t.getDataVencimento() : t.getData();
        if (vencimento.isBefore(LocalDate.now())) {
            return StatusTransacao.ATRASADA;
        }
        return StatusTransacao.PENDENTE;
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
        dto.setDataVencimento(t.getDataVencimento());
        dto.setDataPagamento(t.getDataPagamento());
        dto.setDataCancelamento(t.getDataCancelamento());
        dto.setStatus(computeStatus(t));
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
