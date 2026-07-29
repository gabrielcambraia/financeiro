package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.DirecaoTransferencia;
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
        return findByFilters(month, contaId, tipo, categoriaId, null, null);
    }

    // dataVencimentoFim presente troca o critério de "competência dentro do mês"
    // (data) por "vencimento dentro do período" (dataVencimento), sem limite
    // inferior quando dataVencimentoInicio vem nulo — usado pelo link "Ver
    // todas" do bloco de vencimentos do painel, que sempre exclui canceladas
    // e já pagas (mesmo critério de "pendente" do próprio bloco).
    public List<TransacaoDTO> findByFilters(String month, Long contaId, TipoTransacao tipo, Long categoriaId,
                                             LocalDate dataVencimentoInicio, LocalDate dataVencimentoFim) {
        Long espacoId = contextoEspaco.espacoAtual();

        List<Transacao> raw;
        if (dataVencimentoFim != null) {
            LocalDate inicio = dataVencimentoInicio != null ? dataVencimentoInicio : LocalDate.of(1970, 1, 1);
            raw = contaId != null
                    ? repository.findByEspacoIdAndContaIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
                            espacoId, contaId, inicio, dataVencimentoFim)
                    : repository.findByEspacoIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
                            espacoId, inicio, dataVencimentoFim);
        } else {
            YearMonth ym = YearMonth.parse(month);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            raw = contaId != null
                    ? repository.findByEspacoIdAndContaIdAndDataBetweenOrderByDataDesc(espacoId, contaId, start, end)
                    : repository.findByEspacoIdAndDataBetweenOrderByDataDesc(espacoId, start, end);
        }

        return raw.stream()
                .filter(t -> tipo == null || t.getTipo() == tipo)
                .filter(t -> categoriaId == null
                        || (t.getCategoria() != null && t.getCategoria().getId().equals(categoriaId)))
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public List<TransacaoDTO> create(TransacaoDTO dto) {
        if (dto.getTipo() == TipoTransacao.TRANSFERENCIA) {
            return criarTransferencia(dto);
        }

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
                    contaService.adjustBalance(conta, computeDelta(t));
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
                contaService.adjustBalance(conta, computeDelta(t));
            }
            return criadas.stream().map(this::toDTO).toList();
        }
    }

    private List<TransacaoDTO> criarTransferencia(TransacaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioId = contextoUsuario.usuarioAtual();

        if (dto.getContaDestinoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta destino é obrigatória para transferência");
        }
        if (dto.getContaDestinoId().equals(dto.getContaId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta destino deve ser diferente da conta origem");
        }

        Conta origem = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        Conta destino = contaRepository.findByIdAndEspacoId(dto.getContaDestinoId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta destino não encontrada"));

        boolean quitarNaCriacao = dto.getQuitarNaCriacao() == null || dto.getQuitarNaCriacao();
        boolean paga = quitarNaCriacao && !dto.getData().isAfter(LocalDate.now());
        String transferenciaId = UUID.randomUUID().toString();

        Transacao saida = buildTransacao(dto, origem, null, espacoId, usuarioId);
        saida.setDirecaoTransferencia(DirecaoTransferencia.SAIDA);
        saida.setTransferenciaId(transferenciaId);
        saida.setSaldoAjustado(paga);
        saida.setDataPagamento(paga ? dto.getData() : null);
        repository.save(saida);

        Transacao entrada = buildTransacao(dto, destino, null, espacoId, usuarioId);
        entrada.setDirecaoTransferencia(DirecaoTransferencia.ENTRADA);
        entrada.setTransferenciaId(transferenciaId);
        entrada.setSaldoAjustado(paga);
        entrada.setDataPagamento(paga ? dto.getData() : null);
        repository.save(entrada);

        if (paga) {
            contaService.adjustBalance(origem, computeDelta(saida));
            contaService.adjustBalance(destino, computeDelta(entrada));
        }

        return List.of(toDTO(saida), toDTO(entrada));
    }

    @Transactional
    public TransacaoDTO update(Long id, TransacaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao existente = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        if (dto.getDataPagamento() != null) {
            validarDataPagamento(dto.getDataPagamento());
        }

        if (existente.getTipo() == TipoTransacao.TRANSFERENCIA) {
            return atualizarTransferencia(existente, dto);
        }

        Conta novaConta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        Categoria novaCategoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        boolean estavaAjustada = existente.isSaldoAjustado();
        // A quitação é guiada pela data de pagamento informada, não mais por
        // "data <= hoje" — quitação é manual (ver create()).
        boolean novaPaga = dto.getDataPagamento() != null && existente.getDataCancelamento() == null;

        // Reverte o saldo antigo apenas se ele foi aplicado
        if (estavaAjustada) {
            contaService.adjustBalance(existente.getConta(), computeDelta(existente).negate());
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
            contaService.adjustBalance(novaConta, computeDelta(existente));
        }

        return toDTO(existente);
    }

    // Edição de transferência não troca as contas envolvidas (só valor, data,
    // descrição e quitação) — para mudar origem/destino, cancele e crie outra.
    private TransacaoDTO atualizarTransferencia(Transacao perna, TransacaoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        boolean novaPaga = dto.getDataPagamento() != null && perna.getDataCancelamento() == null;
        LocalDate novaData = dto.getData();
        LocalDate novoVencimento = dto.getDataVencimento() != null ? dto.getDataVencimento() : novaData;

        List<Transacao> par = repository.findByEspacoIdAndTransferenciaId(espacoId, perna.getTransferenciaId());
        for (Transacao perna2 : par) {
            if (perna2.isSaldoAjustado()) {
                contaService.adjustBalance(perna2.getConta(), computeDelta(perna2).negate());
            }
            perna2.setValor(dto.getValor());
            perna2.setDescricao(dto.getDescricao());
            perna2.setData(novaData);
            perna2.setDataVencimento(novoVencimento);
            perna2.setDataPagamento(novaPaga ? dto.getDataPagamento() : null);
            perna2.setSaldoAjustado(novaPaga);
            repository.save(perna2);
            if (novaPaga) {
                contaService.adjustBalance(perna2.getConta(), computeDelta(perna2));
            }
        }

        return toDTO(repository.findByIdAndEspacoId(perna.getId(), espacoId).orElseThrow());
    }

    @Transactional
    public void delete(Long id, String scope) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        if (t.getTipo() == TipoTransacao.TRANSFERENCIA) {
            List<Transacao> par = repository.findByEspacoIdAndTransferenciaId(espacoId, t.getTransferenciaId());
            par.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                    contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
            repository.deleteAll(par);
            return;
        }

        if ("GRUPO".equals(scope) && t.getGrupoParcelaId() != null) {
            List<Transacao> grupo = repository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId());
            grupo.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                    contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
            repository.deleteAll(grupo);
        } else if ("FUTURAS".equals(scope)) {
            if (t.getGrupoParcelaId() != null) {
                List<Transacao> futuras = repository.findByEspacoIdAndGrupoParcelaIdAndDataGreaterThanEqual(
                        espacoId, t.getGrupoParcelaId(), t.getData());
                futuras.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                        contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
                repository.deleteAll(futuras);
            } else if (t.isFixa()) {
                List<Transacao> futuras = repository.findByEspacoIdAndFixaTrueAndDataGreaterThanEqual(espacoId, t.getData());
                futuras.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                        contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
                repository.deleteAll(futuras);
            } else {
                if (t.isSaldoAjustado()) {
                    contaService.adjustBalance(t.getConta(), computeDelta(t).negate());
                }
                repository.delete(t);
            }
        } else {
            if (t.isSaldoAjustado()) {
                contaService.adjustBalance(t.getConta(), computeDelta(t).negate());
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

        List<Transacao> alvos = t.getTipo() == TipoTransacao.TRANSFERENCIA
                ? repository.findByEspacoIdAndTransferenciaId(espacoId, t.getTransferenciaId())
                : List.of(t);
        for (Transacao leg : alvos) {
            if (!leg.isSaldoAjustado()) {
                contaService.adjustBalance(leg.getConta(), computeDelta(leg));
                leg.setSaldoAjustado(true);
            }
            leg.setDataPagamento(dataPg);
            repository.save(leg);
        }
        return toDTO(repository.findByIdAndEspacoId(id, espacoId).orElseThrow());
    }

    @Transactional
    public TransacaoDTO estornar(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        List<Transacao> alvos = t.getTipo() == TipoTransacao.TRANSFERENCIA
                ? repository.findByEspacoIdAndTransferenciaId(espacoId, t.getTransferenciaId())
                : List.of(t);
        for (Transacao leg : alvos) {
            if (leg.isSaldoAjustado()) {
                contaService.adjustBalance(leg.getConta(), computeDelta(leg).negate());
                leg.setSaldoAjustado(false);
            }
            leg.setDataPagamento(null);
            repository.save(leg);
        }
        return toDTO(repository.findByIdAndEspacoId(id, espacoId).orElseThrow());
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
            return; // já cancelada, idempotente — também trava a recursão da perna abaixo
        }
        if (t.isSaldoAjustado()) {
            contaService.adjustBalance(t.getConta(), computeDelta(t).negate());
            t.setSaldoAjustado(false);
        }
        t.setDataCancelamento(LocalDate.now());
        repository.save(t);

        if (t.getTipo() == TipoTransacao.TRANSFERENCIA && t.getTransferenciaId() != null) {
            repository.findByEspacoIdAndTransferenciaId(t.getEspacoId(), t.getTransferenciaId())
                    .forEach(this::cancelarTransacao);
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

    private BigDecimal computeDelta(Transacao t) {
        if (t.getTipo() == TipoTransacao.TRANSFERENCIA) {
            return t.getDirecaoTransferencia() == DirecaoTransferencia.ENTRADA ? t.getValor() : t.getValor().negate();
        }
        return t.getTipo() == TipoTransacao.RECEITA ? t.getValor() : t.getValor().negate();
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
        dto.setTransferenciaId(t.getTransferenciaId());
        dto.setDirecaoTransferencia(t.getDirecaoTransferencia());

        if (t.getTipo() == TipoTransacao.TRANSFERENCIA && t.getTransferenciaId() != null) {
            repository.findByEspacoIdAndTransferenciaId(t.getEspacoId(), t.getTransferenciaId()).stream()
                    .filter(perna -> !perna.getId().equals(t.getId()))
                    .findFirst()
                    .ifPresent(par -> dto.setContaVinculada(contaService.toDTO(par.getConta())));
        }

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
