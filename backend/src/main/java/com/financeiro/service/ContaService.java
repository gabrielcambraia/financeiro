package com.financeiro.service;

import com.financeiro.context.ContextoFilial;
import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.ContaDTO;
import com.financeiro.entity.Banco;
import com.financeiro.entity.Conta;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.BancoRepository;
import com.financeiro.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContaService {

    private final ContaRepository repository;
    private final BancoRepository bancoRepository;
    private final BancoService bancoService;
    private final ContextoEspaco contextoEspaco;
    private final ContextoFilial contextoFilial;
    private final ServicoFilial servicoFilial;

    public List<ContaDTO> findAll() {
        Long espacoId = contextoEspaco.espacoAtual();
        Long filialId = contextoFilial.filialAtual();
        List<Conta> contas = filialId != null
                ? repository.findByEspacoIdFiltradoPorFilial(espacoId, filialId)
                : repository.findByEspacoId(espacoId);
        return contas.stream().map(this::toDTO).toList();
    }

    public ContaDTO create(ContaDTO dto) {
        if (dto.getSaldoInicial() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "saldoInicial é obrigatório");
        }
        Long espacoId = contextoEspaco.espacoAtual();
        Conta conta = Conta.builder()
                .nome(dto.getNome())
                .tipo(dto.getTipo())
                .saldoInicial(dto.getSaldoInicial())
                .saldo(dto.getSaldoInicial())
                .cor(dto.getCor())
                .icone(dto.getIcone())
                .banco(resolverBanco(dto.getBancoId()))
                .espacoId(espacoId)
                .filialId(servicoFilial.resolverParaCadastro(dto.getFilialId(), espacoId))
                .build();
        return toDTO(repository.save(conta));
    }

    public ContaDTO update(Long id, ContaDTO dto) {
        Conta conta = repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada: " + id));
        // saldo e saldoInicial não são editáveis: só mudam via adjustBalance (movimentações)
        conta.setNome(dto.getNome());
        conta.setTipo(dto.getTipo());
        conta.setCor(dto.getCor());
        conta.setIcone(dto.getIcone());
        conta.setBanco(resolverBanco(dto.getBancoId()));
        conta.setFilialId(dto.getFilialId());
        return toDTO(repository.save(conta));
    }

    private Banco resolverBanco(Long bancoId) {
        return bancoId != null ? bancoRepository.findById(bancoId).orElse(null) : null;
    }

    public void delete(Long id) {
        Conta conta = repository.findByIdAndEspacoId(id, contextoEspaco.espacoAtual())
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Conta não encontrada: " + id));
        repository.delete(conta);
    }

    public ContaDTO toDTO(Conta c) {
        ContaDTO dto = new ContaDTO();
        dto.setId(c.getId());
        dto.setNome(c.getNome());
        dto.setTipo(c.getTipo());
        dto.setSaldo(c.getSaldo());
        dto.setSaldoInicial(c.getSaldoInicial());
        dto.setCor(c.getCor());
        dto.setIcone(c.getIcone());
        dto.setFilialId(c.getFilialId());
        if (c.getBanco() != null) {
            dto.setBancoId(c.getBanco().getId());
            dto.setBanco(bancoService.toDTO(c.getBanco()));
        }
        return dto;
    }

    public void adjustBalance(Conta conta, java.math.BigDecimal delta) {
        conta.setSaldo(conta.getSaldo().add(delta));
        repository.save(conta);
    }
}
