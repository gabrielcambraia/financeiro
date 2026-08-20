package com.financeiro.service;

import com.financeiro.context.ContextoEntidade;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.context.ContextoUsuario;
import com.financeiro.dto.RecorrenciaDTO;
import com.financeiro.entity.Recorrencia;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.CategoriaRepository;
import com.financeiro.repository.CentroCustoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.RecorrenciaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecorrenciaService {

    private final RecorrenciaRepository repository;
    private final GeradorLancamentoRecorrencia gerador;
    private final ContextoEspaco contextoEspaco;
    private final ContextoUsuario contextoUsuario;
    private final ContextoEntidade contextoEntidade;
    private final EntityManager entityManager;
    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CentroCustoRepository centroCustoRepository;

    public List<RecorrenciaDTO> listar() {
        Long espacoId = contextoEspaco.espacoAtual();
        Long entidadeId = contextoEntidade.entidadeAtual();
        return repository.findByEspacoIdFiltrado(espacoId, entidadeId)
                .stream().map(this::toDTO).toList();
    }

    public RecorrenciaDTO buscar(Long id) {
        return toDTO(carregar(id));
    }

    @Transactional
    public RecorrenciaDTO criar(RecorrenciaDTO dto) {
        Long espacoId = contextoEspaco.espacoAtual();
        Long usuarioId = contextoUsuario.usuarioAtual();
        validar(dto);
        validarPosseDosVinculos(dto, espacoId);

        Recorrencia r = Recorrencia.builder()
                .espacoId(espacoId)
                .usuarioId(usuarioId)
                .tipo(dto.getTipo())
                .tipoPagamento(dto.getTipoPagamento())
                .contaId(dto.getContaId())
                .cartaoId(dto.getCartaoId())
                .categoriaId(dto.getCategoriaId())
                .centroCustoId(dto.getCentroCustoId())
                .valor(dto.getValor())
                .descricao(dto.getDescricao())
                .diaCompetencia(dto.getDiaCompetencia())
                .diaVencimento(dto.getDiaVencimento())
                .debitoAutomatico(dto.isDebitoAutomatico())
                .ativa(true)
                .dataInicio(dto.getDataInicio() != null ? dto.getDataInicio() : LocalDate.now().withDayOfMonth(1))
                .dataFim(dto.getDataFim())
                .build();
        r = repository.save(r);

        // Gera o mês corrente imediatamente se a dataInicio já chegou
        YearMonth agora = YearMonth.now();
        if (!YearMonth.from(r.getDataInicio()).isAfter(agora)) {
            gerador.gerarParaMes(r, agora);
        }

        // Força recarga do banco para que as associações EAGER sejam populadas no DTO
        // (findById() retornaria a instância do cache L1 da mesma transação)
        entityManager.refresh(r);
        return toDTO(r);
    }

    @Transactional
    public RecorrenciaDTO atualizar(Long id, RecorrenciaDTO dto) {
        Recorrencia r = carregar(id);
        validar(dto);
        validarPosseDosVinculos(dto, r.getEspacoId());

        r.setTipo(dto.getTipo());
        r.setTipoPagamento(dto.getTipoPagamento());
        r.setContaId(dto.getContaId());
        r.setCartaoId(dto.getCartaoId());
        r.setCategoriaId(dto.getCategoriaId());
        r.setCentroCustoId(dto.getCentroCustoId());
        r.setValor(dto.getValor());
        r.setDescricao(dto.getDescricao());
        r.setDiaCompetencia(dto.getDiaCompetencia());
        r.setDiaVencimento(dto.getDiaVencimento());
        r.setDebitoAutomatico(dto.isDebitoAutomatico());
        r.setDataInicio(dto.getDataInicio());
        r.setDataFim(dto.getDataFim());
        r = repository.save(r);

        // Edição não retroage sobre o lançamento já gerado no mês corrente
        // (origemRecorrenciaId) — vale a partir da próxima geração. flush()
        // antes do refresh() é necessário: refresh() não força o UPDATE pendente
        // primeiro, então sem o flush ele recarregaria os valores antigos do banco.
        entityManager.flush();
        entityManager.refresh(r);
        return toDTO(r);
    }

    @Transactional
    public RecorrenciaDTO alternarAtiva(Long id, boolean ativa) {
        Recorrencia r = carregar(id);
        r.setAtiva(ativa);
        return toDTO(repository.save(r));
    }

    @Transactional
    public void excluir(Long id) {
        Recorrencia r = carregar(id);
        repository.delete(r);
    }

    @Transactional
    public RecorrenciaDTO gerarMesAtual(Long id) {
        Recorrencia r = carregar(id);
        gerador.gerarParaMes(r, YearMonth.now());
        return toDTO(r);
    }

    private Recorrencia carregar(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        return repository.findByIdAndEspacoId(id, espacoId)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Recorrência não encontrada"));
    }

    private void validar(RecorrenciaDTO dto) {
        if (dto.getTipoPagamento() == TipoPagamento.DEBITO && dto.getContaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta é obrigatória para recorrência em débito");
        }
        if (dto.getTipoPagamento() == TipoPagamento.CREDITO && dto.getCartaoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cartão é obrigatório para recorrência em crédito");
        }
    }

    // Impede vincular a recorrência a conta/cartão/categoria/centro de custo de outro espaço
    // (os ids vêm do cliente e, sem essa checagem, um usuário poderia referenciar recursos alheios).
    private void validarPosseDosVinculos(RecorrenciaDTO dto, Long espacoId) {
        if (dto.getContaId() != null) {
            contaRepository.findByIdAndEspacoId(dto.getContaId(), espacoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta não encontrada"));
        }
        if (dto.getCartaoId() != null) {
            cartaoRepository.findByIdAndEspacoId(dto.getCartaoId(), espacoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cartão não encontrado"));
        }
        if (dto.getCategoriaId() != null) {
            categoriaRepository.findByIdAndEspacoId(dto.getCategoriaId(), espacoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria não encontrada"));
        }
        if (dto.getCentroCustoId() != null) {
            centroCustoRepository.findByIdAndEspacoId(dto.getCentroCustoId(), espacoId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Centro de custo não encontrado"));
        }
    }

    public RecorrenciaDTO toDTO(Recorrencia r) {
        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setId(r.getId());
        dto.setTipo(r.getTipo());
        dto.setTipoPagamento(r.getTipoPagamento());
        dto.setContaId(r.getContaId());
        dto.setCartaoId(r.getCartaoId());
        dto.setCategoriaId(r.getCategoriaId());
        dto.setCentroCustoId(r.getCentroCustoId());
        dto.setValor(r.getValor());
        dto.setDescricao(r.getDescricao());
        dto.setDiaCompetencia(r.getDiaCompetencia());
        dto.setDiaVencimento(r.getDiaVencimento());
        dto.setDebitoAutomatico(r.isDebitoAutomatico());
        dto.setAtiva(r.isAtiva());
        dto.setDataInicio(r.getDataInicio());
        dto.setDataFim(r.getDataFim());

        if (r.getConta() != null) dto.setContaNome(r.getConta().getNome());
        if (r.getCartao() != null) {
            dto.setCartaoNome(r.getCartao().getNome());
            dto.setCartaoCor(r.getCartao().getCor());
        }
        if (r.getCategoria() != null) {
            dto.setCategoriaNome(r.getCategoria().getNome());
            dto.setCategoriaIcone(r.getCategoria().getIcone());
            dto.setCategoriaCor(r.getCategoria().getCor());
        }
        if (r.getCentroCusto() != null) {
            dto.setCentroCustoNome(r.getCentroCusto().getNome());
            dto.setCentroCustoCor(r.getCentroCusto().getCor());
        }

        // Próxima geração = próximo mês após o atual com ativa=true
        if (r.isAtiva()) {
            YearMonth proximo = YearMonth.now().plusMonths(1);
            if (r.getDataFim() == null || !YearMonth.from(r.getDataFim()).isBefore(proximo)) {
                dto.setProximaGeracaoMes(proximo.toString());
            }
        }

        return dto;
    }
}
