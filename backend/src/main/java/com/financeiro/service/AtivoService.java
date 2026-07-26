package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.AtivoDTO;
import com.financeiro.dto.MovimentacaoAtivoDTO;
import com.financeiro.dto.PatrimonioDTO;
import com.financeiro.entity.Ativo;
import com.financeiro.entity.Conta;
import com.financeiro.entity.MovimentacaoAtivo;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoAtivo;
import com.financeiro.entity.enums.TipoMovimentacaoAtivo;
import com.financeiro.entity.enums.TipoPagamento;
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

    public List<AtivoDTO> findAll() {
        Long espacoId = contextoEspaco.espacoAtual();
        List<Ativo> ativos = repository.findByEspacoIdOrderByCriadoEmDesc(espacoId);
        BigDecimal total = ativos.stream().map(Ativo::getValorAtual).reduce(BigDecimal.ZERO, BigDecimal::add);
        return ativos.stream().map(a -> toDTO(a, total)).toList();
    }

    public List<MovimentacaoAtivoDTO> movimentacoes(Long ativoId) {
        Long espacoId = contextoEspaco.espacoAtual();
        buscar(ativoId);
        return movimentacaoRepository.findByEspacoIdAndAtivoIdOrderByDataDesc(espacoId, ativoId).stream()
                .map(this::toMovimentacaoDTO)
                .toList();
    }

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
                .build();
        return toDTO(repository.save(ativo), null);
    }

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
        return toDTO(repository.save(ativo), null);
    }

    public void delete(Long id) {
        Ativo ativo = buscar(id);
        if (ativo.getValorAtual().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Resgate o valor investido antes de excluir o ativo");
        }
        repository.delete(ativo);
    }

    public AtivoDTO cancelar(Long id) {
        Ativo ativo = buscar(id);
        ativo.setDataCancelamento(LocalDate.now());
        return toDTO(repository.save(ativo), null);
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
        if (totalCarteira != null && totalCarteira.compareTo(BigDecimal.ZERO) > 0) {
            dto.setPercentualCarteira(a.getValorAtual().divide(totalCarteira, 4, RoundingMode.HALF_UP).doubleValue() * 100);
        }
        return dto;
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
