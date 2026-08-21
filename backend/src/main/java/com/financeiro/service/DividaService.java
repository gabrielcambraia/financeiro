package com.financeiro.service;

import com.financeiro.context.ContextoFilial;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.CategoriaDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.DividaDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.Categoria;
import com.financeiro.entity.Conta;
import com.financeiro.entity.Divida;
import com.financeiro.entity.Transacao;
import com.financeiro.entity.enums.EscopoExclusao;
import com.financeiro.entity.enums.StatusDivida;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.DividaRepository;
import com.financeiro.repository.TransacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DividaService {

    private final DividaRepository repository;
    private final ContaRepository contaRepository;
    private final CategoriaRepository categoriaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;
    private final ContextoFilial contextoFilial;

    public List<DividaDTO> findAll() {
        Long espacoId = contextoEspaco.espacoAtual();
        Long filialId = contextoFilial.filialAtual();
        List<Divida> lista = filialId != null
                ? repository.findByEspacoIdFiltradoPorFilial(espacoId, filialId)
                : repository.findByEspacoIdOrderByCriadoEmDesc(espacoId);
        return lista.stream().map(this::toDTO).toList();
    }

    public List<TransacaoDTO> parcelas(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Divida divida = buscar(id);
        return transacaoRepository.findByEspacoIdAndGrupoParcelaId(espacoId, divida.getGrupoParcelaId()).stream()
                .sorted(Comparator.comparing(Transacao::getNumeroParcela))
                .map(transacaoService::toDTO)
                .toList();
    }

    @Transactional
    public DividaDTO create(DividaDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioId = contextoUsuario.usuarioAtual();
        Conta conta = contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada"));
        Categoria categoria = dto.getCategoriaId() != null
                ? categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId).orElse(null)
                : null;

        String grupoId = UUID.randomUUID().toString();
        int totalParcelas = dto.getTotalParcelas();
        BigDecimal valorParcela = dto.getValorTotal().divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_UP);
        BigDecimal somaParcelasIniciais = valorParcela.multiply(BigDecimal.valueOf(totalParcelas - 1));
        BigDecimal valorUltimaParcela = dto.getValorTotal().subtract(somaParcelasIniciais);

        // Dívida é um registro de acordo, não uma compra já paga — todas as
        // parcelas nascem pendentes, independente da data (mesmo a primeira).
        for (int i = 1; i <= totalParcelas; i++) {
            LocalDate dataParcela = dto.getDataInicio().plusMonths(i - 1);
            BigDecimal valor = i == totalParcelas ? valorUltimaParcela : valorParcela;
            Transacao parcela = Transacao.builder()
                    .conta(conta)
                    .categoria(categoria)
                    .tipo(TipoTransacao.DESPESA)
                    .tipoPagamento(TipoPagamento.DEBITO)
                    .valor(valor)
                    .descricao(dto.getDescricao())
                    .data(dataParcela)
                    .dataVencimento(dataParcela)
                    .saldoAjustado(false)
                    .fixa(false)
                    .totalParcelas(totalParcelas)
                    .numeroParcela(i)
                    .grupoParcelaId(grupoId)
                    .espacoId(espacoId)
                    .usuarioId(usuarioId)
                    .build();
            transacaoRepository.save(parcela);
        }

        Divida divida = Divida.builder()
                .descricao(dto.getDescricao())
                .credor(dto.getCredor())
                .valorTotal(dto.getValorTotal())
                .totalParcelas(totalParcelas)
                .conta(conta)
                .categoria(categoria)
                .grupoParcelaId(grupoId)
                .dataInicio(dto.getDataInicio())
                .espacoId(espacoId)
                .usuarioId(usuarioId)
                .build();
        return toDTO(repository.save(divida));
    }

    @Transactional
    public DividaDTO cancelar(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Divida divida = buscar(id);
        List<Transacao> parcelas = transacaoRepository.findByEspacoIdAndGrupoParcelaId(espacoId, divida.getGrupoParcelaId());
        boolean temParcelaPaga = parcelas.stream()
                .anyMatch(t -> t.isSaldoAjustado() && t.getDataCancelamento() == null);
        if (temParcelaPaga) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Estorne todas as parcelas pagas antes de cancelar a dívida");
        }
        if (!parcelas.isEmpty()) {
            transacaoService.cancelar(parcelas.get(0).getId(), EscopoExclusao.GRUPO);
        }
        divida.setDataCancelamento(LocalDate.now());
        return toDTO(repository.save(divida));
    }

    public void delete(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Divida divida = buscar(id);
        boolean temParcelaPaga = transacaoRepository.findByEspacoIdAndGrupoParcelaId(espacoId, divida.getGrupoParcelaId())
                .stream().anyMatch(Transacao::isSaldoAjustado);
        if (temParcelaPaga) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Esta dívida já tem parcelas pagas — cancele em vez de excluir");
        }
        transacaoRepository.deleteAll(transacaoRepository.findByEspacoIdAndGrupoParcelaId(espacoId, divida.getGrupoParcelaId()));
        repository.delete(divida);
    }

    public RespostaImpacto calcularImpactoCancelamento(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Divida divida = buscar(id);
        List<Transacao> parcelas = transacaoRepository
                .findByEspacoIdAndGrupoParcelaId(espacoId, divida.getGrupoParcelaId());
        boolean temParcelaPaga = parcelas.stream()
                .anyMatch(t -> t.isSaldoAjustado() && t.getDataCancelamento() == null);
        if (temParcelaPaga) {
            return new RespostaImpacto(true,
                    "Estorne todas as parcelas pagas antes de cancelar a dívida",
                    List.of(), null);
        }
        List<RespostaImpacto.ItemImpacto> itens = parcelas.stream()
                .filter(t -> t.getDataCancelamento() == null)
                .map(t -> new RespostaImpacto.ItemImpacto(
                        "PENDENTE",
                        "Parcela " + t.getNumeroParcela() + "/" + t.getTotalParcelas()
                                + " — vence " + t.getDataVencimento(),
                        t.getValor()))
                .toList();
        return new RespostaImpacto(false, null, itens, null);
    }

    public RespostaImpacto calcularImpactoExclusao(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Divida divida = buscar(id);
        List<Transacao> parcelas = transacaoRepository
                .findByEspacoIdAndGrupoParcelaId(espacoId, divida.getGrupoParcelaId());
        boolean bloqueado = parcelas.stream().anyMatch(Transacao::isSaldoAjustado);
        String motivo = bloqueado
                ? "Esta dívida já tem parcelas pagas — cancele em vez de excluir" : null;
        List<RespostaImpacto.ItemImpacto> itens = bloqueado ? List.of()
                : parcelas.stream()
                        .map(t -> new RespostaImpacto.ItemImpacto(
                                "PENDENTE",
                                "Parcela " + t.getNumeroParcela() + "/" + t.getTotalParcelas()
                                        + " — vence " + t.getDataVencimento(),
                                t.getValor()))
                        .toList();
        return new RespostaImpacto(bloqueado, motivo, itens, null);
    }

    private Divida buscar(Long id) {
        return repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Dívida não encontrada: " + id));
    }

    private DividaDTO toDTO(Divida d) {
        Long espacoId = d.getEspacoId();
        List<Transacao> parcelas = transacaoRepository.findByEspacoIdAndGrupoParcelaId(espacoId, d.getGrupoParcelaId());

        BigDecimal valorPago = parcelas.stream().filter(Transacao::isSaldoAjustado)
                .map(Transacao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valorCancelado = parcelas.stream().filter(t -> t.getDataCancelamento() != null)
                .map(Transacao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valorRestante = d.getValorTotal().subtract(valorPago).subtract(valorCancelado);
        int parcelasPagas = (int) parcelas.stream().filter(Transacao::isSaldoAjustado).count();

        Transacao proxima = parcelas.stream()
                .filter(t -> !t.isSaldoAjustado() && t.getDataCancelamento() == null)
                .min(Comparator.comparing(Transacao::getDataVencimento))
                .orElse(null);

        StatusDivida status;
        if (d.getDataCancelamento() != null) {
            status = StatusDivida.CANCELADA;
        } else if (proxima == null) {
            status = StatusDivida.QUITADA;
        } else if (proxima.getDataVencimento().isBefore(LocalDate.now())) {
            status = StatusDivida.ATRASADA;
        } else {
            status = StatusDivida.EM_DIA;
        }

        DividaDTO dto = new DividaDTO();
        dto.setId(d.getId());
        dto.setDescricao(d.getDescricao());
        dto.setCredor(d.getCredor());
        dto.setValorTotal(d.getValorTotal());
        dto.setTotalParcelas(d.getTotalParcelas());
        dto.setContaId(d.getConta().getId());
        dto.setDataInicio(d.getDataInicio());
        dto.setDataCancelamento(d.getDataCancelamento());
        dto.setValorPago(valorPago);
        dto.setValorRestante(valorRestante);
        dto.setParcelasPagas(parcelasPagas);
        dto.setStatus(status);
        if (proxima != null) {
            dto.setProximaParcelaData(proxima.getDataVencimento());
            dto.setProximaParcelaValor(proxima.getValor());
        }

        ContaDTO contaDTO = new ContaDTO();
        contaDTO.setId(d.getConta().getId());
        contaDTO.setNome(d.getConta().getNome());
        contaDTO.setTipo(d.getConta().getTipo());
        contaDTO.setSaldo(d.getConta().getSaldo());
        contaDTO.setCor(d.getConta().getCor());
        contaDTO.setIcone(d.getConta().getIcone());
        dto.setConta(contaDTO);

        if (d.getCategoria() != null) {
            dto.setCategoriaId(d.getCategoria().getId());
            CategoriaDTO catDTO = new CategoriaDTO();
            catDTO.setId(d.getCategoria().getId());
            catDTO.setNome(d.getCategoria().getNome());
            catDTO.setTipo(d.getCategoria().getTipo());
            catDTO.setCor(d.getCategoria().getCor());
            catDTO.setIcone(d.getCategoria().getIcone());
            dto.setCategoria(catDTO);
        }

        return dto;
    }
}
