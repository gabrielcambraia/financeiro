package com.financeiro.service;

import com.financeiro.entity.Plano;
import com.financeiro.entity.enums.CodigoPlano;
import com.financeiro.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoPlano {

    private final PlanoRepository planoRepository;

    @Transactional(readOnly = true)
    public List<Plano> listar() {
        return planoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Plano buscarPorCodigo(CodigoPlano codigo) {
        return planoRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado: " + codigo));
    }

    @Transactional
    public Plano atualizarLimite(Long planoId, int novoLimite) {
        Plano plano = planoRepository.findById(planoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        plano.setLimiteEntidades(novoLimite);
        return planoRepository.save(plano);
    }

    @Transactional
    public Plano alterarAtivo(Long planoId, boolean ativo) {
        Plano plano = planoRepository.findById(planoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        plano.setAtivo(ativo);
        return planoRepository.save(plano);
    }
}
