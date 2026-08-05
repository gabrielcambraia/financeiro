package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.AtivoDTO;
import com.financeiro.dto.MovimentacaoAtivoDTO;
import com.financeiro.dto.PatrimonioDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.entity.Ativo;
import com.financeiro.entity.Conta;
import com.financeiro.entity.MovimentacaoAtivo;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoAtivo;
import com.financeiro.entity.enums.TipoMovimentacaoAtivo;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoRemuneracao;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.AtivoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.MovimentacaoAtivoRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AtivoService {

    private final AtivoRepository repository;
    private final ContaRepository contaRepository;
    private final MovimentacaoAtivoRepository movimentacaoRepository;
    private final TransacaoRepository transacaoRepository;
    private final ContaService contaService;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;
    private final ContextoEntidade contextoEntidade;

    public List<AtivoDTO> findAll() {
        Long espacoId = contextoEspaco.espacoAtual();
        Long entidadeId = contextoEntidade.entidadeAtual();
        List<Ativo> ativos = entidadeId != null
                ? repository.findByEspacoIdFiltradoPorEntidade(espacoId, entidadeId)
                : repository.findByEspacoIdOrderByCriadoEmDesc(espacoId);
        BigDecimal total = ativos.stream().map(Ativo::getValorAtual).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Uma única query pra todas as movimentações do espaço, agrupada em memória por
        // ativo — evita N+1 (uma query de movimentações por ativo) ao calcular os
        // campos derivados (rentabilidade, IR estimado) de cada card.
        Map<Long, List<MovimentacaoAtivo>> movimentosPorAtivo = movimentacaoRepository.findByEspacoId(espacoId).stream()
                .collect(Collectors.groupingBy(m -> m.getAtivo().getId()));

        return ativos.stream()
                .map(a -> toDTO(a, total, movimentosPorAtivo.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    public List<MovimentacaoAtivoDTO> movimentacoes(Long ativoId) {
        Long espacoId = contextoEspaco.espacoAtual();
        buscar(ativoId);
        return movimentacaoRepository.findByEspacoIdAndAtivoIdOrderByDataDesc(espacoId, ativoId).stream()
                .map(this::toMovimentacaoDTO)
                .toList();
    }

    @Transactional
    public AtivoDTO create(AtivoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Conta conta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        Ativo ativo = Ativo.builder()
                .nome(dto.getNome())
                .tipo(dto.getTipo())
                .conta(conta)
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .espacoId(espacoId)
                .usuarioId(contextoUsuario.usuarioAtual())
                .remuneracaoTipo(dto.getRemuneracaoTipo() != null ? dto.getRemuneracaoTipo() : TipoRemuneracao.NENHUMA)
                .taxa(dto.getTaxa())
                .inicioRendimento(dto.getInicioRendimento())
                .isentoIr(dto.isIsentoIr())
                .entidadeId(dto.getEntidadeId())
                .build();
        ativo = repository.save(ativo);

        if (dto.getValorInicial() != null && dto.getValorInicial().compareTo(BigDecimal.ZERO) > 0) {
            MovimentacaoAtivoDTO aporteInicial = new MovimentacaoAtivoDTO();
            aporteInicial.setValor(dto.getValorInicial());
            aporteInicial.setData(dto.getDataInicial());
            aporteInicial.setContaId(dto.getContaId());
            return aportar(ativo.getId(), aporteInicial);
        }
        return toDTO(ativo, null);
    }

    // Nota: dto.getValorInicial()/getDataInicial() são deliberadamente ignorados
    // aqui — só se aplicam na criação (ver create()), não existe coluna para eles
    // e não devem poder alterar um ativo já existente.
    public AtivoDTO update(Long id, AtivoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Ativo ativo = buscar(id);
        Conta conta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        ativo.setNome(dto.getNome());
        ativo.setTipo(dto.getTipo());
        ativo.setConta(conta);
        ativo.setCor(dto.getCor());
        ativo.setIcone(dto.getIcone());
        ativo.setRemuneracaoTipo(dto.getRemuneracaoTipo() != null ? dto.getRemuneracaoTipo() : TipoRemuneracao.NENHUMA);
        ativo.setTaxa(dto.getTaxa());
        ativo.setInicioRendimento(dto.getInicioRendimento());
        ativo.setIsentoIr(dto.isIsentoIr());
        return toDTO(repository.save(ativo), null);
    }

    @Transactional
    public void delete(Long id) {
        Ativo ativo = buscar(id);
        if (ativo.getValorAtual().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Resgate o valor investido antes de excluir o ativo");
        }
        movimentacaoRepository.deleteByAtivoId(ativo.getId());
        repository.delete(ativo);
    }

    @Transactional
    public AtivoDTO cancelar(Long id) {
        Ativo ativo = buscar(id);
        movimentacaoRepository.findByAtivoIdOrderByDataAsc(ativo.getId()).stream()
                .filter(m -> m.getTransacao() != null && m.getTransacao().getDataCancelamento() == null)
                .map(MovimentacaoAtivo::getTransacao)
                .forEach(t -> {
                    if (t.isSaldoAjustado()) {
                        BigDecimal delta = t.getTipo() == TipoTransacao.RECEITA
                                ? t.getValor().negate() : t.getValor();
                        contaService.adjustBalance(t.getConta(), delta);
                        t.setSaldoAjustado(false);
                    }
                    t.setDataCancelamento(LocalDate.now());
                    transacaoRepository.save(t);
                });
        ativo.setValorAtual(BigDecimal.ZERO);
        ativo.setDataCancelamento(LocalDate.now());
        return toDTO(repository.save(ativo), null);
    }

    public RespostaImpacto calcularImpactoCancelamento(Long id) {
        Ativo ativo = buscar(id);
        List<RespostaImpacto.ItemImpacto> itens = movimentacaoRepository
                .findByAtivoIdOrderByDataAsc(ativo.getId()).stream()
                .filter(m -> m.getTransacao() != null && m.getTransacao().getDataCancelamento() == null)
                .map(m -> new RespostaImpacto.ItemImpacto(
                        m.getTipo().name(),
                        m.getTransacao().getDescricao() + " em " + m.getData(),
                        m.getValor()))
                .toList();
        return new RespostaImpacto(false, null, itens, null);
    }

    public RespostaImpacto calcularImpactoExclusao(Long id) {
        Ativo ativo = buscar(id);
        boolean bloqueado = ativo.getValorAtual().compareTo(BigDecimal.ZERO) > 0;
        String motivo = bloqueado ? "Resgate o valor investido antes de excluir o ativo" : null;
        List<RespostaImpacto.ItemImpacto> itens = bloqueado ? List.of()
                : movimentacaoRepository.findByAtivoIdOrderByDataAsc(ativo.getId()).stream()
                        .map(m -> new RespostaImpacto.ItemImpacto(
                                m.getTipo().name(),
                                m.getTransacao() != null
                                        ? m.getTransacao().getDescricao() + " em " + m.getData()
                                        : m.getTipo().name() + " em " + m.getData(),
                                m.getValor()))
                        .toList();
        return new RespostaImpacto(bloqueado, motivo, itens, null);
    }

    @Transactional
    public AtivoDTO aportar(Long id, MovimentacaoAtivoDTO dto) {
        Ativo ativo = buscar(id);
        garantirAtivo(ativo);
        Long espacoId = contextoEspaco.espacoAtual();
        Conta conta = contaRepository.findByIdAndEspacoId(exigirConta(dto), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        LocalDate data = validarData(dto.getData());

        Transacao t = Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(dto.getValor())
                .descricao("Aporte: " + ativo.getNome())
                .data(data)
                .dataVencimento(data)
                .dataPagamento(data)
                .saldoAjustado(true)
                .fixa(false)
                .espacoId(espacoId)
                .usuarioId(contextoUsuario.usuarioAtual())
                .build();
        transacaoRepository.save(t);
        contaService.adjustBalance(conta, dto.getValor().negate());

        registrarMovimentacao(ativo, TipoMovimentacaoAtivo.APORTE, dto.getValor(), data, t);
        ativo.setValorAtual(ativo.getValorAtual().add(dto.getValor()));
        if (ativo.getInicioRendimento() == null) {
            // Default do início de rendimento é a data do primeiro aporte (ver spec de
            // rendimento automático) — só se aplica a ativos com indexador configurado.
            ativo.setInicioRendimento(data);
        }
        return toDTO(repository.save(ativo), null);
    }

    @Transactional
    public AtivoDTO resgatar(Long id, MovimentacaoAtivoDTO dto) {
        Ativo ativo = buscar(id);
        garantirAtivo(ativo);
        if (dto.getValor().compareTo(ativo.getValorAtual()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor maior que o investido no ativo");
        }
        Long espacoId = contextoEspaco.espacoAtual();
        Conta conta = contaRepository.findByIdAndEspacoId(exigirConta(dto), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        LocalDate data = validarData(dto.getData());

        Transacao t = Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.RECEITA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(dto.getValor())
                .descricao("Resgate: " + ativo.getNome())
                .data(data)
                .dataVencimento(data)
                .dataPagamento(data)
                .saldoAjustado(true)
                .fixa(false)
                .espacoId(espacoId)
                .usuarioId(contextoUsuario.usuarioAtual())
                .build();
        transacaoRepository.save(t);
        contaService.adjustBalance(conta, dto.getValor());

        registrarMovimentacao(ativo, TipoMovimentacaoAtivo.RESGATE, dto.getValor(), data, t);
        ativo.setValorAtual(ativo.getValorAtual().subtract(dto.getValor()));
        return toDTO(repository.save(ativo), null);
    }

    @Transactional
    public AtivoDTO registrarRendimento(Long id, MovimentacaoAtivoDTO dto) {
        Ativo ativo = buscar(id);
        garantirAtivo(ativo);
        LocalDate data = validarData(dto.getData());

        registrarMovimentacao(ativo, TipoMovimentacaoAtivo.RENDIMENTO, dto.getValor(), data, null);
        ativo.setValorAtual(ativo.getValorAtual().add(dto.getValor()));
        return toDTO(repository.save(ativo), null);
    }

    public PatrimonioDTO patrimonio(int meses) {
        Long espacoId = contextoEspaco.espacoAtual();
        List<Ativo> ativos = repository.findByEspacoIdOrderByCriadoEmDesc(espacoId);
        BigDecimal total = ativos.stream().map(Ativo::getValorAtual).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<TipoAtivo, BigDecimal> porTipoMapa = ativos.stream()
                .collect(Collectors.groupingBy(Ativo::getTipo,
                        Collectors.reducing(BigDecimal.ZERO, Ativo::getValorAtual, BigDecimal::add)));
        List<PatrimonioDTO.ResumoTipo> porTipo = porTipoMapa.entrySet().stream()
                .map(e -> PatrimonioDTO.ResumoTipo.builder().tipo(e.getKey()).valor(e.getValue()).build())
                .sorted(Comparator.comparing(r -> r.getTipo().name()))
                .toList();

        // Reconstrói o patrimônio de meses anteriores "andando pra trás" a partir do
        // valor atual: cada mês perde as movimentações que aconteceram depois dele.
        YearMonth atual = YearMonth.now();
        LocalDate limiteAntigo = atual.minusMonths(meses - 1L).atDay(1).minusDays(1);
        List<MovimentacaoAtivo> movimentos = movimentacaoRepository.findByEspacoIdAndDataAfter(espacoId, limiteAntigo);

        List<PatrimonioDTO.EvolucaoMensal> evolucao = new ArrayList<>();
        for (int i = 0; i < meses; i++) {
            YearMonth mesAlvo = atual.minusMonths(i);
            LocalDate fimMes = mesAlvo.atEndOfMonth();
            BigDecimal aRemover = movimentos.stream()
                    .filter(m -> m.getData().isAfter(fimMes))
                    .map(m -> sinal(m.getTipo()).equals(BigDecimal.ONE) ? m.getValor() : m.getValor().negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            evolucao.add(PatrimonioDTO.EvolucaoMensal.builder()
                    .mes(mesAlvo.toString())
                    .valor(total.subtract(aRemover))
                    .build());
        }
        evolucao.sort(Comparator.comparing(PatrimonioDTO.EvolucaoMensal::getMes));

        return PatrimonioDTO.builder()
                .totalPatrimonio(total)
                .porTipo(porTipo)
                .evolucaoMensal(evolucao)
                .build();
    }

    /**
     * Saldo (valorAtual) de um ativo específico numa data passada, "andando pra
     * trás" a partir do valorAtual atual usando as movimentações posteriores a
     * essa data — mesma técnica usada em {@link #patrimonio(int)}, extraída aqui
     * para reuso pelo {@code AgendadorRendimento} (base de cálculo do rendimento
     * automático de cada mês fechado). Cálculo puro sobre um {@code Ativo} já
     * carregado pelo chamador — que é quem responde pelo escopo de espaço.
     */
    public BigDecimal saldoEm(Ativo ativo, LocalDate data) {
        BigDecimal aRemover = movimentacaoRepository.findByAtivoIdOrderByDataAsc(ativo.getId()).stream()
                .filter(m -> m.getData().isAfter(data))
                .map(m -> sinal(m.getTipo()).equals(BigDecimal.ONE) ? m.getValor() : m.getValor().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ativo.getValorAtual().subtract(aRemover);
    }

    /**
     * Credita rendimento automático (CDI/Selic/IPCA+/pré-fixado) calculado pelo
     * {@code AgendadorRendimento} e marca o mês como rendido, atomicamente. Só
     * registra movimentação/soma {@code valorAtual} quando há valor a creditar,
     * mas {@code rendidoAte} sempre avança — garante que uma falha entre "creditar"
     * e "marcar como processado" não exista, o que faria a próxima execução
     * creditar o mesmo mês de novo. Reaproveita o mesmo caminho de
     * {@code registrarRendimento} manual: só move {@code valorAtual} do ativo,
     * sem criar {@code Transacao} nenhuma (valorização não mexe em saldo de conta).
     */
    @Transactional
    public void creditarRendimentoAutomatico(Ativo ativo, BigDecimal valor, LocalDate data) {
        if (valor.compareTo(BigDecimal.ZERO) > 0) {
            registrarMovimentacao(ativo, TipoMovimentacaoAtivo.RENDIMENTO, valor, data, null);
            ativo.setValorAtual(ativo.getValorAtual().add(valor));
        }
        ativo.setRendidoAte(data);
        repository.save(ativo);
    }

    private BigDecimal sinal(TipoMovimentacaoAtivo tipo) {
        return tipo == TipoMovimentacaoAtivo.RESGATE ? BigDecimal.valueOf(-1) : BigDecimal.ONE;
    }

    private void registrarMovimentacao(Ativo ativo, TipoMovimentacaoAtivo tipo, BigDecimal valor, LocalDate data, Transacao transacao) {
        movimentacaoRepository.save(MovimentacaoAtivo.builder()
                .ativo(ativo)
                .tipo(tipo)
                .valor(valor)
                .data(data)
                .transacao(transacao)
                .espacoId(ativo.getEspacoId())
                .build());
    }

    private Long exigirConta(MovimentacaoAtivoDTO dto) {
        if (dto.getContaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta é obrigatória");
        }
        return dto.getContaId();
    }

    private Ativo buscar(Long id) {
        return repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Ativo não encontrado: " + id));
    }

    private void garantirAtivo(Ativo ativo) {
        if (ativo.getDataCancelamento() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ativo cancelado");
        }
    }

    private LocalDate validarData(LocalDate data) {
        LocalDate resolvida = data != null ? data : LocalDate.now();
        if (resolvida.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data não pode ser no futuro");
        }
        return resolvida;
    }

    private AtivoDTO toDTO(Ativo a, BigDecimal totalCarteira) {
        // Chamado a partir de create/update/aportar/resgatar/etc — um único ativo por
        // vez, então uma query extra de movimentações aqui não tem o problema de N+1
        // que findAll() precisa evitar (lista inteira do espaço).
        List<MovimentacaoAtivo> movimentos = movimentacaoRepository
                .findByEspacoIdAndAtivoIdOrderByDataDesc(a.getEspacoId(), a.getId());
        return toDTO(a, totalCarteira, movimentos);
    }

    private AtivoDTO toDTO(Ativo a, BigDecimal totalCarteira, List<MovimentacaoAtivo> movimentos) {
        AtivoDTO dto = new AtivoDTO();
        dto.setId(a.getId());
        dto.setNome(a.getNome());
        dto.setTipo(a.getTipo());
        dto.setContaId(a.getConta().getId());
        dto.setConta(contaService.toDTO(a.getConta()));
        dto.setCor(a.getCor());
        dto.setIcone(a.getIcone());
        dto.setValorAtual(a.getValorAtual());
        dto.setDataCancelamento(a.getDataCancelamento());
        dto.setRemuneracaoTipo(a.getRemuneracaoTipo());
        dto.setTaxa(a.getTaxa());
        dto.setInicioRendimento(a.getInicioRendimento());
        dto.setIsentoIr(a.isIsentoIr());
        dto.setRendidoAte(a.getRendidoAte());
        if (totalCarteira != null && totalCarteira.compareTo(BigDecimal.ZERO) > 0) {
            dto.setPercentualCarteira(a.getValorAtual().divide(totalCarteira, 4, RoundingMode.HALF_UP).doubleValue() * 100);
        }
        aplicarCamposDerivados(dto, movimentos);
        dto.setEntidadeId(a.getEntidadeId());
        return dto;
    }

    /** Rentabilidade e IR estimado — derivados em memória a partir das movimentações já carregadas (sem query nova). */
    private void aplicarCamposDerivados(AtivoDTO dto, List<MovimentacaoAtivo> movimentos) {
        BigDecimal totalAportado = somaPorTipo(movimentos, TipoMovimentacaoAtivo.APORTE);
        BigDecimal totalResgatado = somaPorTipo(movimentos, TipoMovimentacaoAtivo.RESGATE);
        BigDecimal totalRendimento = somaPorTipo(movimentos, TipoMovimentacaoAtivo.RENDIMENTO);

        dto.setTotalAportado(totalAportado);
        dto.setTotalResgatado(totalResgatado);
        dto.setTotalRendimento(totalRendimento);

        if (totalAportado.compareTo(BigDecimal.ZERO) > 0) {
            dto.setRentabilidadePercentual(totalRendimento.divide(totalAportado, 6, RoundingMode.HALF_UP).doubleValue() * 100);
        } else {
            dto.setRentabilidadePercentual(0.0);
        }

        List<CalculadoraIr.Aporte> aportes = movimentos.stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoAtivo.APORTE)
                .map(m -> new CalculadoraIr.Aporte(m.getData(), m.getValor()))
                .toList();
        long prazoMedioDias = CalculadoraIr.prazoMedioPonderadoDias(aportes, LocalDate.now());
        BigDecimal irEstimado = CalculadoraIr.irEstimado(totalRendimento, prazoMedioDias, dto.isIsentoIr());
        dto.setIrEstimado(irEstimado);
        dto.setValorLiquidoEstimado(dto.getValorAtual().subtract(irEstimado));
    }

    private BigDecimal somaPorTipo(List<MovimentacaoAtivo> movimentos, TipoMovimentacaoAtivo tipo) {
        return movimentos.stream()
                .filter(m -> m.getTipo() == tipo)
                .map(MovimentacaoAtivo::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private MovimentacaoAtivoDTO toMovimentacaoDTO(MovimentacaoAtivo m) {
        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setId(m.getId());
        dto.setTipo(m.getTipo());
        dto.setValor(m.getValor());
        dto.setData(m.getData());
        if (m.getTransacao() != null) {
            dto.setContaId(m.getTransacao().getConta().getId());
        }
        return dto;
    }
}
