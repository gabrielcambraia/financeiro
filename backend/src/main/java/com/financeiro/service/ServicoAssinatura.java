package com.financeiro.service;

import com.financeiro.entity.Assinatura;
import com.financeiro.entity.Plano;
import com.financeiro.entity.enums.CodigoPlano;
import com.financeiro.entity.enums.StatusAssinatura;
import com.financeiro.repository.AssinaturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicoAssinatura {

    private final AssinaturaRepository assinaturaRepository;
    private final ServicoPlano servicoPlano;

    @Transactional(readOnly = true)
    public Assinatura assinaturaDoEspaco(Long espacoId) {
        return assinaturaRepository.findByEspacoId(espacoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura não encontrada para o espaço"));
    }

    @Transactional
    public Assinatura criarParaEspaco(Long espacoId, CodigoPlano codigoPlano) {
        Plano plano = servicoPlano.buscarPorCodigo(codigoPlano);
        Assinatura assinatura = Assinatura.builder()
                .espacoId(espacoId)
                .planoId(plano.getId())
                .status(StatusAssinatura.ATIVA)
                .build();
        return assinaturaRepository.save(assinatura);
    }

    @Transactional
    public Assinatura trocarPlano(Long espacoId, CodigoPlano novoCodigoPlano) {
        Plano novoPlano = servicoPlano.buscarPorCodigo(novoCodigoPlano);
        Assinatura assinatura = assinaturaDoEspaco(espacoId);
        assinatura.setPlanoId(novoPlano.getId());
        assinatura.setStatus(StatusAssinatura.ATIVA);
        log.info("Plano do espaço {} alterado para {}", espacoId, novoCodigoPlano);
        return assinaturaRepository.save(assinatura);
    }
}
