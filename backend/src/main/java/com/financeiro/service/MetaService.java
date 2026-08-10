package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.MetaDTO;
import com.financeiro.dto.MetaMovimentoDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Meta;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.MetaRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetaService {

    private final MetaRepository repository;
    private final ContaRepository contaRepository;
    private final TransacaoRepository transacaoRepository;
    private final ContaService contaService;
    private final ContextoEspaco contextoEspaco;
    private final ContextoEntidade contextoEntidade;
    private final ContextoUsuario contextoUsuario;

    public List<MetaDTO> findAll() {
        Long espacoId = contextoEspaco.espacoAtual();
        Long entidadeId = contextoEntidade.entidadeAtual();
        List<Meta> metas = entidadeId != null
                ? repository.findByEspacoIdFiltradoPorEntidade(espacoId, entidadeId)
                : repository.findByEspacoIdOrderByCriadoEmDesc(espacoId);
        return metas.stream().map(this::toDTO).toList();
    }

    public MetaDTO create(MetaDTO dto) {
        Meta meta = Meta.builder()
                .nome(dto.getNome())
                .valorAlvo(dto.getValorAlvo())
                .prazo(dto.getPrazo())
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .espacoId(contextoEspaco.espacoAtual())
                .usuarioId(contextoUsuario.usuarioAtual())
                .entidadeId(dto.getEntidadeId())
                .build();
        return toDTO(repository.save(meta));
    }

    public MetaDTO update(Long id, MetaDTO dto) {
        Meta meta = buscar(id);
        meta.setNome(dto.getNome());
        meta.setValorAlvo(dto.getValorAlvo());
        meta.setPrazo(dto.getPrazo());
        meta.setCor(dto.getCor());
        meta.setIcone(dto.getIcone());
        return toDTO(repository.save(meta));
    }

    @Transactional
    public void delete(Long id) {
        Meta meta = buscar(id);
        if (meta.getValorAtual().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Resgate o valor guardado antes de excluir a meta");
        }
        transacaoRepository.desvincularMeta(meta.getId());
        repository.delete(meta);
    }

    @Transactional
    public MetaDTO cancelar(Long id) {
        Meta meta = buscar(id);
        if (meta.getValorAtual().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Resgate todo o valor guardado antes de cancelar a meta");
        }
        meta.setDataCancelamento(LocalDate.now());
        return toDTO(repository.save(meta));
    }

    public RespostaImpacto calcularImpactoCancelamento(Long id) {
        Meta meta = buscar(id);
        if (meta.getValorAtual().compareTo(BigDecimal.ZERO) > 0) {
            return new RespostaImpacto(true,
                    "Resgate todo o valor guardado antes de cancelar a meta",
                    List.of(), null);
        }
        return new RespostaImpacto(false, null, List.of(), null);
    }

    public RespostaImpacto calcularImpactoExclusao(Long id) {
        Meta meta = buscar(id);
        boolean bloqueado = meta.getValorAtual().compareTo(BigDecimal.ZERO) > 0;
        String motivo = bloqueado ? "Resgate o valor guardado antes de excluir a meta" : null;
        List<RespostaImpacto.ItemImpacto> itens = bloqueado ? List.of()
                : transacaoRepository.findByEspacoIdAndMetaId(meta.getEspacoId(), meta.getId())
                        .stream()
                        .map(t -> new RespostaImpacto.ItemImpacto(
                                t.getTipo().name(),
                                t.getDescricao() + " em " + t.getData(),
                                t.getValor()))
                        .toList();
        return new RespostaImpacto(bloqueado, motivo, itens, null);
    }

    @Transactional
    public MetaDTO aportar(Long id, MetaMovimentoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Meta meta = buscar(id);
        garantirAtiva(meta);
        Conta conta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        LocalDate data = validarData(dto.getData());

        Transacao t = Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.DESPESA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(dto.getValor())
                .descricao("Aporte: " + meta.getNome())
                .data(data)
                .dataVencimento(data)
                .dataPagamento(data)
                .saldoAjustado(true)
                .fixa(false)
                .meta(meta)
                .espacoId(espacoId)
                .usuarioId(contextoUsuario.usuarioAtual())
                .build();
        transacaoRepository.save(t);
        contaService.adjustBalance(conta, dto.getValor().negate());

        meta.setValorAtual(meta.getValorAtual().add(dto.getValor()));
        return toDTO(repository.save(meta));
    }

    @Transactional
    public MetaDTO resgatar(Long id, MetaMovimentoDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Meta meta = buscar(id);
        garantirAtiva(meta);
        if (dto.getValor().compareTo(meta.getValorAtual()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor maior que o guardado na meta");
        }
        Conta conta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        LocalDate data = validarData(dto.getData());

        Transacao t = Transacao.builder()
                .conta(conta)
                .tipo(TipoTransacao.RECEITA)
                .tipoPagamento(TipoPagamento.DEBITO)
                .valor(dto.getValor())
                .descricao("Resgate: " + meta.getNome())
                .data(data)
                .dataVencimento(data)
                .dataPagamento(data)
                .saldoAjustado(true)
                .fixa(false)
                .meta(meta)
                .espacoId(espacoId)
                .usuarioId(contextoUsuario.usuarioAtual())
                .build();
        transacaoRepository.save(t);
        contaService.adjustBalance(conta, dto.getValor());

        meta.setValorAtual(meta.getValorAtual().subtract(dto.getValor()));
        return toDTO(repository.save(meta));
    }

    private Meta buscar(Long id) {
        return repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Meta não encontrada: " + id));
    }

    private void garantirAtiva(Meta meta) {
        if (meta.getDataCancelamento() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meta cancelada");
        }
    }

    private LocalDate validarData(LocalDate data) {
        LocalDate resolvida = data != null ? data : LocalDate.now();
        if (resolvida.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data não pode ser no futuro");
        }
        return resolvida;
    }

    private MetaDTO toDTO(Meta m) {
        MetaDTO dto = new MetaDTO();
        dto.setId(m.getId());
        dto.setNome(m.getNome());
        dto.setValorAlvo(m.getValorAlvo());
        dto.setValorAtual(m.getValorAtual());
        dto.setPrazo(m.getPrazo());
        dto.setCor(m.getCor());
        dto.setIcone(m.getIcone());
        dto.setDataCancelamento(m.getDataCancelamento());
        dto.setEntidadeId(m.getEntidadeId());

        boolean concluida = m.getValorAtual().compareTo(m.getValorAlvo()) >= 0;
        dto.setConcluida(concluida);
        double percentual = m.getValorAlvo().compareTo(BigDecimal.ZERO) == 0 ? 0
                : m.getValorAtual().divide(m.getValorAlvo(), 4, RoundingMode.HALF_UP).doubleValue() * 100;
        dto.setPercentualConcluido(Math.min(100, percentual));

        if (!concluida && m.getDataCancelamento() == null) {
            long mesesDecorridos = Math.max(1, ChronoUnit.MONTHS.between(
                    YearMonth.from(m.getCriadoEm()), YearMonth.from(LocalDate.now())) + 1);
            BigDecimal mediaMensal = m.getValorAtual().divide(BigDecimal.valueOf(mesesDecorridos), 2, RoundingMode.HALF_UP);
            if (mediaMensal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal faltante = m.getValorAlvo().subtract(m.getValorAtual());
                dto.setMesesEstimados(faltante.divide(mediaMensal, 0, RoundingMode.CEILING).intValue());
            }
        }

        return dto;
    }
}
