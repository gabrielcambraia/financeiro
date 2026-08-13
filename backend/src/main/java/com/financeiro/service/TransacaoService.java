package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.Ativo;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Fatura;
import com.financeiro.entity.Meta;
import com.financeiro.entity.MovimentacaoAtivo;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.DirecaoTransferencia;
import com.financeiro.entity.enums.EscopoAtualizacao;
import com.financeiro.entity.enums.EscopoExclusao;
import com.financeiro.entity.enums.StatusTransacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoMovimentacaoAtivo;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.AtivoRepository;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.DividaRepository;
import com.financeiro.repository.FaturaRepository;
import com.financeiro.repository.MetaRepository;
import com.financeiro.repository.MovimentacaoAtivoRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransacaoService {

    private final TransacaoRepository repository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaService contaService;
    private final AtivoRepository ativoRepository;
    private final MovimentacaoAtivoRepository movimentacaoAtivoRepository;
    private final MetaRepository metaRepository;
    private final FaturaRepository faturaRepository;
    private final DividaRepository dividaRepository;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;
    private final ContextoEntidade contextoEntidade;

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
        Long entidadeId = contextoEntidade.entidadeAtual();

        List<Transacao> raw;
        if (dataVencimentoFim != null) {
            LocalDate inicio = dataVencimentoInicio != null ? dataVencimentoInicio : LocalDate.of(1970, 1, 1);
            if (contaId != null) {
                raw = entidadeId != null
                        ? repository.findByEspacoIdAndContaIdAndDataVencimentoBetweenFiltradoPorEntidade(
                                espacoId, contaId, inicio, dataVencimentoFim, entidadeId)
                        : repository.findByEspacoIdAndContaIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
                                espacoId, contaId, inicio, dataVencimentoFim);
            } else {
                raw = entidadeId != null
                        ? repository.findVencimentosPorPeriodoFiltradoPorEntidade(
                                espacoId, inicio, dataVencimentoFim, entidadeId)
                        : repository.findByEspacoIdAndDataVencimentoBetweenAndDataPagamentoIsNullAndDataCancelamentoIsNullOrderByDataVencimentoAsc(
                                espacoId, inicio, dataVencimentoFim);
            }
        } else {
            YearMonth ym = YearMonth.parse(month);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            if (contaId != null) {
                raw = entidadeId != null
                        ? repository.findByEspacoIdAndContaIdAndDataBetweenFiltradoPorEntidade(
                                espacoId, contaId, start, end, entidadeId)
                        : repository.findByEspacoIdAndContaIdAndDataBetweenOrderByDataDesc(espacoId, contaId, start, end);
            } else {
                raw = entidadeId != null
                        ? repository.findByEspacoIdAndDataBetweenFiltradoPorEntidade(
                                espacoId, start, end, entidadeId)
                        : repository.findByEspacoIdAndDataBetweenOrderByDataDesc(espacoId, start, end);
            }
        }

        List<Transacao> filtradas = raw.stream()
                .filter(t -> tipo == null || t.getTipo() == tipo)
                .filter(t -> categoriaId == null
                        || (t.getCategoria() != null && t.getCategoria().getId().equals(categoriaId)))
                .toList();
        List<TransacaoDTO> dtos = filtradas.stream().map(this::toDTO).toList();
        enriquecerOrigemDerivadaLote(filtradas, dtos);
        return dtos;
    }

    @Transactional
    public List<TransacaoDTO> create(TransacaoDTO dto) {
        if (dto.getTipo() == TipoTransacao.TRANSFERENCIA) {
            return criarTransferencia(dto);
        }
        validarTipoPagamento(dto.getTipo(), dto.getTipoPagamento());

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
                // O valor informado é o total da compra — cada parcela recebe uma
                // fração dele, não o valor integral repetido.
                t.setValor(CalculadoraParcelas.valorParcela(dto.getValor(), dto.getTotalParcelas(), i));
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
                // Define origemFixaId na cabeça (auto-referência) para identificar a série.
                t.setOrigemFixaId(t.getId());
                t.setSerieAtiva(true);
                repository.save(t);

                Long cabecaId = t.getId();
                LocalDate dataBase = dto.getData();
                for (int i = 1; i <= 11; i++) {
                    LocalDate dataFutura = dataBase.plusMonths(i);
                    Transacao futura = buildTransacao(dto, conta, categoria, espacoId, usuarioId);
                    futura.setData(dataFutura);
                    futura.setDataVencimento(dataFutura);
                    futura.setSaldoAjustado(false);
                    futura.setDataPagamento(null);
                    futura.setOrigemFixaId(cabecaId);
                    futura.setSerieAtiva(true);
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
    public TransacaoDTO update(Long id, TransacaoDTO dto, EscopoAtualizacao scope) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao existente = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        verificarNaoDerivada(existente);

        if (dto.getDataPagamento() != null) {
            validarDataPagamento(dto.getDataPagamento());
        }

        if (existente.getTipo() == TipoTransacao.TRANSFERENCIA) {
            return atualizarTransferencia(existente, dto);
        }
        validarTipoPagamento(dto.getTipo(), dto.getTipoPagamento());

        Conta novaConta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        Categoria novaCategoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        if (scope == EscopoAtualizacao.FUTURAS && existente.isFixa() && existente.getOrigemFixaId() != null) {
            return atualizarFixasFuturas(existente, dto, novaConta, novaCategoria, espacoId);
        }

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
        existente.setDebitoAutomatico(dto.getTipo() == TipoTransacao.DESPESA && dto.getTipoPagamento() == TipoPagamento.DEBITO && dto.isDebitoAutomatico());
        existente.setSaldoAjustado(novaPaga);
        repository.save(existente);

        // Aplica o novo saldo apenas se a transação está paga
        if (novaPaga) {
            contaService.adjustBalance(novaConta, computeDelta(existente));
        }

        return toDTO(existente);
    }

    private TransacaoDTO atualizarFixasFuturas(Transacao t, TransacaoDTO dto,
                                                Conta novaConta, Categoria novaCategoria, Long espacoId) {
        List<Transacao> futuras = repository.findByEspacoIdAndOrigemFixaIdAndDataGreaterThanEqual(
                espacoId, t.getOrigemFixaId(), t.getData());

        bloquearSePagaOuCanceladaNoEscopo(futuras, t.getData());

        // Reprojetar cada linha pelo seu offset mensal em relação à cabeça original,
        // preservando a cadência mensal independente do número de dias entre meses.
        LocalDate novaDataBase = dto.getData();
        LocalDate novoVencBase = dto.getDataVencimento() != null ? dto.getDataVencimento() : dto.getData();

        Long novaCabecaId = t.getId();
        Long origemAntiga = t.getOrigemFixaId();

        for (Transacao f : futuras) {
            // Se a linha inicial (t) já estiver paga, reverte o saldo antigo e
            // reaplicará com o novo valor/conta abaixo. Linhas futuras são
            // garantidamente não pagas (bloqueadas pelo validador acima).
            boolean eraSaldoAjustado = f.isSaldoAjustado();
            BigDecimal deltaAntigo = eraSaldoAjustado ? computeDelta(f) : null;
            Conta contaAntiga = f.getConta();

            long mesesDaCabeca = ChronoUnit.MONTHS.between(YearMonth.from(t.getData()), YearMonth.from(f.getData()));

            f.setConta(novaConta);
            f.setCategoria(novaCategoria);
            f.setTipo(dto.getTipo());
            f.setTipoPagamento(dto.getTipoPagamento());
            f.setValor(dto.getValor());
            f.setDescricao(dto.getDescricao());
            f.setEntidadeId(dto.getEntidadeId());
            f.setDebitoAutomatico(dto.getTipo() == TipoTransacao.DESPESA && dto.getTipoPagamento() == TipoPagamento.DEBITO && dto.isDebitoAutomatico());
            f.setData(novaDataBase.plusMonths(mesesDaCabeca));
            f.setDataVencimento(novoVencBase.plusMonths(mesesDaCabeca));
            f.setOrigemFixaId(novaCabecaId);
            f.setSerieAtiva(true);
            repository.save(f);

            if (eraSaldoAjustado) {
                contaService.adjustBalance(contaAntiga, deltaAntigo.negate());
                contaService.adjustBalance(novaConta, computeDelta(f));
            }
        }

        // Encerra o trilho antigo: as linhas históricas (data < t.data) ficam no
        // banco mas o agendador não as reprojetará para meses futuros.
        repository.encerrarTrilhoAntigo(espacoId, origemAntiga, t.getData());

        return toDTO(repository.findByIdAndEspacoId(t.getId(), espacoId).orElseThrow());
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
    public void delete(Long id, EscopoExclusao scope) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        verificarBloqueioFatura(t);

        if (t.getTipo() == TipoTransacao.TRANSFERENCIA) {
            List<Transacao> par = repository.findByEspacoIdAndTransferenciaId(espacoId, t.getTransferenciaId());
            par.forEach(this::verificarCanceladaParaExclusao);
            par.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                    contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
            repository.deleteAll(par);
            return;
        }

        if (scope == EscopoExclusao.GRUPO && t.getGrupoParcelaId() != null) {
            List<Transacao> grupo = repository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId());
            grupo.forEach(this::verificarCanceladaParaExclusao);
            grupo.forEach(this::propagarExclusaoParaOrigem);
            grupo.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                    contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
            repository.deleteAll(grupo);
        } else if (scope == EscopoExclusao.FUTURAS) {
            if (t.getGrupoParcelaId() != null) {
                List<Transacao> futuras = repository.findByEspacoIdAndGrupoParcelaIdAndDataGreaterThanEqual(
                        espacoId, t.getGrupoParcelaId(), t.getData());
                futuras.forEach(this::verificarCanceladaParaExclusao);
                futuras.forEach(this::propagarExclusaoParaOrigem);
                futuras.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                        contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
                repository.deleteAll(futuras);
            } else if (t.isFixa()) {
                if (t.getOrigemFixaId() == null) {
                    // Fixa criada antes da V23: auto-sana como cabeça da própria série,
                    // igual ao que o agendador faz no startup.
                    t.setOrigemFixaId(t.getId());
                    t.setSerieAtiva(true);
                    repository.save(t);
                }
                List<Transacao> futuras = repository.findByEspacoIdAndOrigemFixaIdAndDataGreaterThanEqual(
                        espacoId, t.getOrigemFixaId(), t.getData());
                futuras.forEach(this::verificarCanceladaParaExclusao);
                futuras.forEach(this::propagarExclusaoParaOrigem);
                futuras.stream().filter(Transacao::isSaldoAjustado).forEach(tx ->
                        contaService.adjustBalance(tx.getConta(), computeDelta(tx).negate()));
                repository.deleteAll(futuras);
                repository.encerrarTrilhoAntigo(espacoId, t.getOrigemFixaId(), t.getData());
            } else {
                verificarCanceladaParaExclusao(t);
                propagarExclusaoParaOrigem(t);
                if (t.isSaldoAjustado()) {
                    contaService.adjustBalance(t.getConta(), computeDelta(t).negate());
                }
                repository.delete(t);
            }
        } else {
            verificarCanceladaParaExclusao(t);
            propagarExclusaoParaOrigem(t);
            if (t.isSaldoAjustado()) {
                contaService.adjustBalance(t.getConta(), computeDelta(t).negate());
            }
            repository.delete(t);
        }
    }

    @Transactional
    public TransacaoDTO pagar(Long id, String dataPagamentoStr, String multaStr) {
        LocalDate dataPagamento = null;
        if (dataPagamentoStr != null) {
            try {
                dataPagamento = LocalDate.parse(dataPagamentoStr);
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de pagamento inválida");
            }
        }
        BigDecimal multa = null;
        if (multaStr != null) {
            try {
                multa = new BigDecimal(multaStr);
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multa inválida");
            }
            if (multa.compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multa não pode ser negativa");
            }
        }
        return pagar(id, dataPagamento, multa);
    }

    @Transactional
    public TransacaoDTO pagar(Long id, LocalDate dataPagamento) {
        return pagar(id, dataPagamento, null);
    }

    @Transactional
    public TransacaoDTO pagar(Long id, LocalDate dataPagamento, BigDecimal multa) {
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
                // A multa precisa estar gravada ANTES de calcular o delta aplicado
                // ao saldo — computeDelta soma multa apenas para DESPESA.
                if (multa != null && leg.getTipo() == TipoTransacao.DESPESA) {
                    leg.setMulta(multa);
                }
                contaService.adjustBalance(leg.getConta(), computeDelta(leg));
                leg.setSaldoAjustado(true);
            }
            leg.setDataPagamento(dataPg);
            repository.save(leg);
        }
        return toDTO(repository.findByIdAndEspacoId(id, espacoId).orElseThrow());
    }

    // Chamado pelo AgendadorDebitoAutomatico — não usa contextoEspaco (request-scoped).
    @Transactional
    public void quitarDebitoAutomatico(Transacao t) {
        if (t.isSaldoAjustado() || t.getDataCancelamento() != null || t.getDataPagamento() != null) return;
        LocalDate hoje = LocalDate.now();
        contaService.adjustBalance(t.getConta(), computeDelta(t));
        t.setSaldoAjustado(true);
        t.setDataPagamento(hoje);
        repository.save(t);
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
            leg.setMulta(null);
            repository.save(leg);
        }
        return toDTO(repository.findByIdAndEspacoId(id, espacoId).orElseThrow());
    }

    @Transactional
    public TransacaoDTO cancelar(Long id, EscopoExclusao scope) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        verificarBloqueioFatura(t);

        if (scope == EscopoExclusao.GRUPO && t.getGrupoParcelaId() != null) {
            repository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId())
                    .forEach(tx -> cancelarTransacao(tx, true));
        } else if (scope == EscopoExclusao.FUTURAS) {
            if (t.getGrupoParcelaId() != null) {
                repository.findByEspacoIdAndGrupoParcelaIdAndDataGreaterThanEqual(
                        espacoId, t.getGrupoParcelaId(), t.getData())
                        .forEach(tx -> cancelarTransacao(tx, true));
            } else if (t.isFixa()) {
                if (t.getOrigemFixaId() == null) {
                    t.setOrigemFixaId(t.getId());
                    t.setSerieAtiva(true);
                    repository.save(t);
                }
                List<Transacao> futuras = repository.findByEspacoIdAndOrigemFixaIdAndDataGreaterThanEqual(
                        espacoId, t.getOrigemFixaId(), t.getData());
                bloquearSePagaOuCanceladaNoEscopo(futuras, t.getData());
                futuras.forEach(tx -> cancelarTransacao(tx, true));
                repository.encerrarTrilhoAntigo(espacoId, t.getOrigemFixaId(), t.getData());
            } else {
                cancelarTransacao(t, true);
            }
        } else {
            cancelarTransacao(t, true);
        }

        return toDTO(repository.findByIdAndEspacoId(id, espacoId).orElseThrow());
    }

    // Bloqueia a operação em lote se houver alguma linha ESTRITAMENTE futura
    // (data > dataInicio) já paga ou cancelada. A linha inicial (data == dataInicio)
    // pode estar paga; nesse caso o saldo é revertido/reaplicado normalmente.
    private void bloquearSePagaOuCanceladaNoEscopo(List<Transacao> transacoes, LocalDate dataInicio) {
        boolean temPagaOuCancelada = transacoes.stream()
                .filter(t -> t.getData().isAfter(dataInicio))
                .anyMatch(t -> t.getDataPagamento() != null || t.getDataCancelamento() != null);
        if (temPagaOuCancelada) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Não é possível aplicar esta operação: há lançamentos pagos ou cancelados no escopo selecionado. " +
                    "Desfaça o pagamento/cancelamento ou selecione uma data de início posterior.");
        }
    }

    private void validarTipoPagamento(TipoTransacao tipo, TipoPagamento tipoPagamento) {
        if (tipo == TipoTransacao.RECEITA && tipoPagamento == TipoPagamento.CREDITO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receita não pode ser em crédito");
        }
    }

    private void validarDataPagamento(LocalDate dataPagamento) {
        if (dataPagamento.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Data de pagamento não pode ser no futuro");
        }
    }

    private void cancelarTransacao(Transacao t, boolean propagarOrigem) {
        if (t.getDataCancelamento() != null) {
            return; // já cancelada, idempotente — também trava a recursão da perna abaixo
        }
        boolean eraSaldoAjustado = t.isSaldoAjustado();
        if (eraSaldoAjustado) {
            contaService.adjustBalance(t.getConta(), computeDelta(t).negate());
            t.setSaldoAjustado(false);
        }
        t.setMulta(null);
        t.setDataCancelamento(LocalDate.now());
        repository.save(t);

        if (propagarOrigem && eraSaldoAjustado) {
            propagarCancelamentoParaOrigem(t);
        }

        if (t.getTipo() == TipoTransacao.TRANSFERENCIA && t.getTransferenciaId() != null) {
            repository.findByEspacoIdAndTransferenciaId(t.getEspacoId(), t.getTransferenciaId())
                    .forEach(perna -> cancelarTransacao(perna, false));
        }
    }

    private void propagarCancelamentoParaOrigem(Transacao t) {
        if (t.getMeta() != null) {
            Meta meta = t.getMeta();
            if (t.getTipo() == TipoTransacao.DESPESA) {
                meta.setValorAtual(meta.getValorAtual().subtract(t.getValor()));
            } else if (t.getTipo() == TipoTransacao.RECEITA) {
                meta.setValorAtual(meta.getValorAtual().add(t.getValor()));
            }
            metaRepository.save(meta);
        }
        movimentacaoAtivoRepository.findByTransacaoId(t.getId()).ifPresent(mov -> {
            Ativo ativo = mov.getAtivo();
            if (mov.getTipo() == TipoMovimentacaoAtivo.APORTE) {
                ativo.setValorAtual(ativo.getValorAtual().subtract(mov.getValor()));
            } else if (mov.getTipo() == TipoMovimentacaoAtivo.RESGATE) {
                ativo.setValorAtual(ativo.getValorAtual().add(mov.getValor()));
            }
            ativoRepository.save(ativo);
            movimentacaoAtivoRepository.delete(mov);
        });
    }

    private void propagarExclusaoParaOrigem(Transacao t) {
        if (t.getMeta() != null && t.isSaldoAjustado()) {
            Meta meta = t.getMeta();
            if (t.getTipo() == TipoTransacao.DESPESA) {
                meta.setValorAtual(meta.getValorAtual().subtract(t.getValor()));
            } else if (t.getTipo() == TipoTransacao.RECEITA) {
                meta.setValorAtual(meta.getValorAtual().add(t.getValor()));
            }
            metaRepository.save(meta);
        }
        movimentacaoAtivoRepository.findByTransacaoId(t.getId()).ifPresent(mov -> {
            if (t.isSaldoAjustado()) {
                Ativo ativo = mov.getAtivo();
                if (mov.getTipo() == TipoMovimentacaoAtivo.APORTE) {
                    ativo.setValorAtual(ativo.getValorAtual().subtract(mov.getValor()));
                } else if (mov.getTipo() == TipoMovimentacaoAtivo.RESGATE) {
                    ativo.setValorAtual(ativo.getValorAtual().add(mov.getValor()));
                }
                ativoRepository.save(ativo);
            }
            movimentacaoAtivoRepository.delete(mov);
        });
    }

    private void verificarBloqueioFatura(Transacao t) {
        faturaRepository.findByTransacaoDespesaId(t.getId()).ifPresent(f ->
            { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação corresponde ao pagamento da fatura do cartão "
                            + f.getCartao().getNome()
                            + ". Para cancelar, acesse a fatura."); });
    }

    public RespostaImpacto calcularImpactoCancelamento(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        var faturaOpt = faturaRepository.findByTransacaoDespesaId(id);
        if (faturaOpt.isPresent()) {
            Fatura f = faturaOpt.get();
            return new RespostaImpacto(true,
                    "Esta transação corresponde ao pagamento da fatura do cartão "
                            + f.getCartao().getNome() + ". Para cancelar, acesse a fatura.",
                    List.of(), null);
        }

        RespostaImpacto.OrigemVinculada origem = null;
        if (t.getMeta() != null) {
            Meta meta = t.getMeta();
            String efeito = t.getTipo() == TipoTransacao.DESPESA
                    ? "Reduzirá a meta \"" + meta.getNome() + "\" em R$ " + t.getValor()
                    : "Adicionará R$ " + t.getValor() + " de volta à meta \"" + meta.getNome() + "\"";
            origem = new RespostaImpacto.OrigemVinculada("META", meta.getId(), meta.getNome(), efeito);
        } else {
            var movOpt = movimentacaoAtivoRepository.findByTransacaoId(id);
            if (movOpt.isPresent()) {
                MovimentacaoAtivo mov = movOpt.get();
                Ativo ativo = mov.getAtivo();
                String efeito = mov.getTipo() == TipoMovimentacaoAtivo.APORTE
                        ? "Reduzirá o investimento \"" + ativo.getNome() + "\" em R$ " + t.getValor()
                        : "Adicionará R$ " + t.getValor() + " de volta ao investimento \"" + ativo.getNome() + "\"";
                origem = new RespostaImpacto.OrigemVinculada("ATIVO", ativo.getId(), ativo.getNome(), efeito);
            } else if (t.getGrupoParcelaId() != null) {
                var dividaOpt = dividaRepository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId());
                if (dividaOpt.isPresent()) {
                    var divida = dividaOpt.get();
                    String efeito = t.isSaldoAjustado()
                            ? "Reverterá o pagamento desta parcela da dívida"
                            : "Esta parcela ainda não estava paga";
                    origem = new RespostaImpacto.OrigemVinculada(
                            "DIVIDA", divida.getId(), divida.getDescricao(), efeito);
                }
            }
        }
        return new RespostaImpacto(false, null, List.of(), origem);
    }

    public RespostaImpacto calcularImpactoExclusao(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Transacao t = repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Transação não encontrada"));

        var faturaOpt = faturaRepository.findByTransacaoDespesaId(id);
        if (faturaOpt.isPresent()) {
            Fatura f = faturaOpt.get();
            return new RespostaImpacto(true,
                    "Esta transação corresponde ao pagamento da fatura do cartão "
                            + f.getCartao().getNome() + ". Para excluir, acesse a fatura.",
                    List.of(), null);
        }

        if (t.getDataCancelamento() == null) {
            return new RespostaImpacto(true,
                    "A transação precisa ser cancelada antes de ser excluída.",
                    List.of(), null);
        }

        RespostaImpacto.OrigemVinculada origem = null;
        if (t.getMeta() != null) {
            Meta meta = t.getMeta();
            String efeito = t.isSaldoAjustado()
                    ? (t.getTipo() == TipoTransacao.DESPESA
                            ? "Reduzirá a meta \"" + meta.getNome() + "\" em R$ " + t.getValor()
                            : "Adicionará R$ " + t.getValor() + " de volta à meta \"" + meta.getNome() + "\"")
                    : "Lançamento pendente — sem impacto no saldo da meta";
            origem = new RespostaImpacto.OrigemVinculada("META", meta.getId(), meta.getNome(), efeito);
        } else {
            var movOpt = movimentacaoAtivoRepository.findByTransacaoId(id);
            if (movOpt.isPresent()) {
                MovimentacaoAtivo mov = movOpt.get();
                Ativo ativo = mov.getAtivo();
                String efeito = t.isSaldoAjustado()
                        ? (mov.getTipo() == TipoMovimentacaoAtivo.APORTE
                                ? "Reduzirá o investimento \"" + ativo.getNome() + "\" em R$ " + t.getValor()
                                : "Adicionará R$ " + t.getValor() + " de volta ao investimento \"" + ativo.getNome() + "\"")
                        : "Lançamento pendente — sem impacto no saldo do investimento";
                origem = new RespostaImpacto.OrigemVinculada("ATIVO", ativo.getId(), ativo.getNome(), efeito);
            } else if (t.getGrupoParcelaId() != null) {
                var dividaOpt = dividaRepository.findByEspacoIdAndGrupoParcelaId(espacoId, t.getGrupoParcelaId());
                if (dividaOpt.isPresent()) {
                    var divida = dividaOpt.get();
                    String efeito = t.isSaldoAjustado()
                            ? "Reverterá o pagamento desta parcela da dívida"
                            : "Esta parcela ainda não estava paga";
                    origem = new RespostaImpacto.OrigemVinculada(
                            "DIVIDA", divida.getId(), divida.getDescricao(), efeito);
                }
            }
        }
        return new RespostaImpacto(false, null, List.of(), origem);
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
                .debitoAutomatico(dto.getTipo() == TipoTransacao.DESPESA && dto.getTipoPagamento() == TipoPagamento.DEBITO && dto.isDebitoAutomatico())
                .espacoId(espacoId)
                .usuarioId(usuarioId)
                .entidadeId(dto.getEntidadeId())
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
        if (t.getTipo() == TipoTransacao.RECEITA) {
            return t.getValor();
        }
        // DESPESA: a multa por atraso, quando informada, é somada ao valor debitado.
        BigDecimal valorEfetivo = t.getValor().add(t.getMulta() != null ? t.getMulta() : BigDecimal.ZERO);
        return valorEfetivo.negate();
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
        dto.setDebitoAutomatico(t.isDebitoAutomatico());
        dto.setTotalParcelas(t.getTotalParcelas());
        dto.setNumeroParcela(t.getNumeroParcela());
        dto.setGrupoParcelaId(t.getGrupoParcelaId());
        dto.setUsuarioId(t.getUsuarioId());
        dto.setTransferenciaId(t.getTransferenciaId());
        dto.setDirecaoTransferencia(t.getDirecaoTransferencia());
        dto.setMulta(t.getMulta());

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

        dto.setEntidadeId(t.getEntidadeId());
        return dto;
    }

    private void verificarCanceladaParaExclusao(Transacao t) {
        if (t.getDataCancelamento() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A transação precisa ser cancelada antes de ser excluída.");
        }
    }

    private void verificarNaoDerivada(Transacao t) {
        if (t.getMeta() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação é derivada de uma meta e não pode ser editada. Cancele-a primeiro, exclua e registre uma nova movimentação na meta.");
        }
        if (movimentacaoAtivoRepository.findByTransacaoId(t.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação é derivada de um investimento e não pode ser editada. Cancele-a primeiro, exclua e registre uma nova movimentação no investimento.");
        }
        if (t.getGrupoParcelaId() != null &&
                dividaRepository.findByEspacoIdAndGrupoParcelaId(t.getEspacoId(), t.getGrupoParcelaId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta transação é derivada de uma dívida e não pode ser editada. Cancele-a primeiro, exclua e gerencie o pagamento pela dívida.");
        }
    }

    private void enriquecerOrigemDerivadaLote(List<Transacao> transacoes, List<TransacaoDTO> dtos) {
        if (transacoes.isEmpty()) return;
        Long espacoId = contextoEspaco.espacoAtual();
        List<Long> ids = transacoes.stream().map(Transacao::getId).toList();
        Set<Long> comAtivo = movimentacaoAtivoRepository.findTransacaoIdsDerivados(ids, espacoId);
        Set<String> grupoIds = transacoes.stream()
                .filter(t -> t.getGrupoParcelaId() != null)
                .map(Transacao::getGrupoParcelaId)
                .collect(Collectors.toSet());
        Set<String> gruposComDivida = grupoIds.isEmpty() ? Set.of()
                : dividaRepository.findGrupoParcelaIdsComDivida(espacoId, grupoIds);
        Map<Long, TransacaoDTO> dtoPorId = dtos.stream()
                .collect(Collectors.toMap(TransacaoDTO::getId, dto -> dto));
        for (Transacao t : transacoes) {
            TransacaoDTO dto = dtoPorId.get(t.getId());
            if (dto == null) continue;
            if (t.getMeta() != null) {
                dto.setOrigemDerivada("META");
            } else if (comAtivo.contains(t.getId())) {
                dto.setOrigemDerivada("ATIVO");
            } else if (t.getGrupoParcelaId() != null && gruposComDivida.contains(t.getGrupoParcelaId())) {
                dto.setOrigemDerivada("DIVIDA");
            }
        }
    }
}
