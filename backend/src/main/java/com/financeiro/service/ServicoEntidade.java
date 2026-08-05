package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.RequisicaoCriarEntidade;
import com.financeiro.dto.RespostaAssinatura;
import com.financeiro.dto.RespostaEntidade;
import com.financeiro.entity.Assinatura;
import com.financeiro.entity.Entidade;
import com.financeiro.entity.Plano;
import com.financeiro.repository.AssinaturaRepository;
import com.financeiro.repository.EntidadeRepository;
import com.financeiro.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoEntidade {

    private final EntidadeRepository entidadeRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final PlanoRepository planoRepository;
    private final CifradorDados cifradorDados;
    private final ValidadorDocumento validadorDocumento;
    private final ContextoEspaco contextoEspaco;

    @Transactional(readOnly = true)
    public List<RespostaEntidade> listar() {
        Long espacoId = contextoEspaco.espacoAtual();
        return entidadeRepository.findByEspacoId(espacoId).stream()
                .map(e -> mapear(e, decifrarDocumento(e)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RespostaEntidade buscar(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Entidade e = buscarPorIdEEspaco(id, espacoId);
        return mapear(e, decifrarDocumento(e));
    }

    @Transactional
    public RespostaEntidade criar(RequisicaoCriarEntidade req) {
        Long espacoId = contextoEspaco.espacoAtual();

        String docLimpo = validadorDocumento.limparEValidar(req.getDocumento(), req.getTipoPessoa());

        // Lock na assinatura do espaço para evitar race na criação simultânea
        Assinatura assinatura = assinaturaRepository.findByEspacoIdWithLock(espacoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Espaço sem assinatura"));

        Plano plano = planoRepository.findById(assinatura.getPlanoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Plano não encontrado"));

        long total = entidadeRepository.countByEspacoId(espacoId);
        if (total >= plano.getLimiteEntidades()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Limite de entidades do plano atingido (" + plano.getLimiteEntidades() + " no plano " + plano.getNome() + ")");
        }

        String hash = cifradorDados.hashDocumento(docLimpo);
        if (entidadeRepository.findByEspacoIdAndDocumentoHash(espacoId, hash).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Documento já cadastrado neste espaço");
        }

        Entidade entidade = Entidade.builder()
                .espacoId(espacoId)
                .tipoPessoa(req.getTipoPessoa())
                .nome(req.getNome())
                .nomeFantasia(req.getNomeFantasia())
                .documentoCifrado(cifradorDados.cifrar(docLimpo))
                .documentoHash(hash)
                .inscricaoEstadual(req.getInscricaoEstadual())
                .dataNascimento(req.getDataNascimento())
                .email(req.getEmail())
                .telefone(req.getTelefone())
                .cep(req.getCep())
                .logradouro(req.getLogradouro())
                .numero(req.getNumero())
                .complemento(req.getComplemento())
                .bairro(req.getBairro())
                .cidade(req.getCidade())
                .uf(req.getUf())
                .build();

        return mapear(entidadeRepository.save(entidade), docLimpo);
    }

    @Transactional
    public RespostaEntidade atualizar(Long id, RequisicaoCriarEntidade req) {
        Long espacoId = contextoEspaco.espacoAtual();
        Entidade entidade = buscarPorIdEEspaco(id, espacoId);

        String docLimpo = validadorDocumento.limparEValidar(req.getDocumento(), req.getTipoPessoa());
        String hash = cifradorDados.hashDocumento(docLimpo);

        if (!hash.equals(entidade.getDocumentoHash())) {
            entidadeRepository.findByEspacoIdAndDocumentoHash(espacoId, hash).ifPresent(e -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Documento já cadastrado neste espaço");
            });
        }

        entidade.setTipoPessoa(req.getTipoPessoa());
        entidade.setNome(req.getNome());
        entidade.setNomeFantasia(req.getNomeFantasia());
        entidade.setDocumentoCifrado(cifradorDados.cifrar(docLimpo));
        entidade.setDocumentoHash(hash);
        entidade.setInscricaoEstadual(req.getInscricaoEstadual());
        entidade.setDataNascimento(req.getDataNascimento());
        entidade.setEmail(req.getEmail());
        entidade.setTelefone(req.getTelefone());
        entidade.setCep(req.getCep());
        entidade.setLogradouro(req.getLogradouro());
        entidade.setNumero(req.getNumero());
        entidade.setComplemento(req.getComplemento());
        entidade.setBairro(req.getBairro());
        entidade.setCidade(req.getCidade());
        entidade.setUf(req.getUf());

        return mapear(entidadeRepository.save(entidade), docLimpo);
    }

    @Transactional
    public void excluir(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Entidade entidade = buscarPorIdEEspaco(id, espacoId);
        entidadeRepository.delete(entidade);
    }

    @Transactional(readOnly = true)
    public RespostaAssinatura resumoAssinatura() {
        Long espacoId = contextoEspaco.espacoAtual();
        Assinatura assinatura = assinaturaRepository.findByEspacoId(espacoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura não encontrada"));
        Plano plano = planoRepository.findById(assinatura.getPlanoId())
                .orElseThrow();
        long usadas = entidadeRepository.countByEspacoId(espacoId);
        return new RespostaAssinatura(
                assinatura.getId(),
                plano.getCodigo(),
                plano.getNome(),
                plano.getLimiteEntidades(),
                usadas,
                assinatura.getStatus(),
                assinatura.getVigenciaInicio(),
                assinatura.getVigenciaFim());
    }

    private Entidade buscarPorIdEEspaco(Long id, Long espacoId) {
        Entidade e = entidadeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entidade não encontrada"));
        if (!e.getEspacoId().equals(espacoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
        return e;
    }

    private String decifrarDocumento(Entidade e) {
        return cifradorDados.decifrar(e.getDocumentoCifrado());
    }

    private RespostaEntidade mapear(Entidade e, String documento) {
        return new RespostaEntidade(
                e.getId(), e.getTipoPessoa(), e.getNome(), e.getNomeFantasia(), documento,
                e.getInscricaoEstadual(), e.getDataNascimento(), e.getEmail(), e.getTelefone(),
                e.getCep(), e.getLogradouro(), e.getNumero(), e.getComplemento(),
                e.getBairro(), e.getCidade(), e.getUf(), e.getCriadoEm(), e.getAtualizadoEm());
    }
}
