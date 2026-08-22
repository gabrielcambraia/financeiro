package com.financeiro.service;

import com.financeiro.context.ContextoEspaco;
import com.financeiro.dto.RequisicaoCriarFilial;
import com.financeiro.dto.RespostaAssinatura;
import com.financeiro.dto.RespostaFilial;
import com.financeiro.entity.Assinatura;
import com.financeiro.entity.Filial;
import com.financeiro.entity.Plano;
import com.financeiro.repository.AssinaturaRepository;
import com.financeiro.repository.FilialRepository;
import com.financeiro.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicoFilial {

    private final FilialRepository filialRepository;
    private final AssinaturaRepository assinaturaRepository;
    private final PlanoRepository planoRepository;
    private final CifradorDados cifradorDados;
    private final ValidadorDocumento validadorDocumento;
    private final ContextoEspaco contextoEspaco;

    @Transactional(readOnly = true)
    public List<RespostaFilial> listar() {
        Long espacoId = contextoEspaco.espacoAtual();
        return filialRepository.findByEspacoId(espacoId).stream()
                .map(f -> mapear(f, decifrarDocumento(f)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RespostaFilial buscar(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Filial f = buscarPorIdEEspaco(id, espacoId);
        return mapear(f, decifrarDocumento(f));
    }

    @Transactional
    public RespostaFilial criar(RequisicaoCriarFilial req) {
        Long espacoId = contextoEspaco.espacoAtual();

        String docLimpo = validadorDocumento.limparEValidar(req.getDocumento(), req.getTipoPessoa());

        Assinatura assinatura = assinaturaRepository.findByEspacoIdWithLock(espacoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Espaço sem assinatura"));

        Plano plano = planoRepository.findById(assinatura.getPlanoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Plano não encontrado"));

        long total = filialRepository.countByEspacoId(espacoId);
        if (total >= plano.getLimiteEntidades()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Limite de filiais do plano atingido (" + plano.getLimiteEntidades() + " no plano " + plano.getNome() + ")");
        }

        String hash = cifradorDados.hashDocumento(docLimpo);
        if (filialRepository.findByEspacoIdAndDocumentoHash(espacoId, hash).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Documento já cadastrado neste espaço");
        }

        Filial filial = Filial.builder()
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

        return mapear(filialRepository.save(filial), docLimpo);
    }

    @Transactional
    public RespostaFilial atualizar(Long id, RequisicaoCriarFilial req) {
        Long espacoId = contextoEspaco.espacoAtual();
        Filial filial = buscarPorIdEEspaco(id, espacoId);

        String docLimpo = validadorDocumento.limparEValidar(req.getDocumento(), req.getTipoPessoa());
        String hash = cifradorDados.hashDocumento(docLimpo);

        if (!hash.equals(filial.getDocumentoHash())) {
            filialRepository.findByEspacoIdAndDocumentoHash(espacoId, hash).ifPresent(f -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Documento já cadastrado neste espaço");
            });
        }

        filial.setTipoPessoa(req.getTipoPessoa());
        filial.setNome(req.getNome());
        filial.setNomeFantasia(req.getNomeFantasia());
        filial.setDocumentoCifrado(cifradorDados.cifrar(docLimpo));
        filial.setDocumentoHash(hash);
        filial.setInscricaoEstadual(req.getInscricaoEstadual());
        filial.setDataNascimento(req.getDataNascimento());
        filial.setEmail(req.getEmail());
        filial.setTelefone(req.getTelefone());
        filial.setCep(req.getCep());
        filial.setLogradouro(req.getLogradouro());
        filial.setNumero(req.getNumero());
        filial.setComplemento(req.getComplemento());
        filial.setBairro(req.getBairro());
        filial.setCidade(req.getCidade());
        filial.setUf(req.getUf());

        return mapear(filialRepository.save(filial), docLimpo);
    }

    @Transactional
    public void excluir(Long id) {
        Long espacoId = contextoEspaco.espacoAtual();
        Filial filial = buscarPorIdEEspaco(id, espacoId);
        filialRepository.delete(filial);
    }

    @Transactional(readOnly = true)
    public RespostaAssinatura resumoAssinatura() {
        Long espacoId = contextoEspaco.espacoAtual();
        Assinatura assinatura = assinaturaRepository.findByEspacoId(espacoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assinatura não encontrada"));
        Plano plano = planoRepository.findById(assinatura.getPlanoId())
                .orElseThrow();
        long usadas = filialRepository.countByEspacoId(espacoId);
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

    /**
     * Resolve o filialId para um cadastro raiz (Conta, Categoria, etc.).
     * Se o payload já traz um id, usa direto. Se não traz e o espaço tem
     * exatamente 1 filial, atribui automaticamente. Caso contrário, nulo
     * (cadastro "global", sem filial específica).
     */
    public Long resolverParaCadastro(Long filialIdDoDto, Long espacoId) {
        if (filialIdDoDto != null) {
            buscarPorIdEEspaco(filialIdDoDto, espacoId);
            return filialIdDoDto;
        }
        List<Filial> lista = filialRepository.findByEspacoId(espacoId);
        return lista.size() == 1 ? lista.get(0).getId() : null;
    }

    private Filial buscarPorIdEEspaco(Long id, Long espacoId) {
        Filial f = filialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Filial não encontrada"));
        if (!f.getEspacoId().equals(espacoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado");
        }
        return f;
    }

    private String decifrarDocumento(Filial f) {
        return cifradorDados.decifrar(f.getDocumentoCifrado());
    }

    private RespostaFilial mapear(Filial f, String documento) {
        return new RespostaFilial(
                f.getId(), f.getTipoPessoa(), f.getNome(), f.getNomeFantasia(), documento,
                f.getInscricaoEstadual(), f.getDataNascimento(), f.getEmail(), f.getTelefone(),
                f.getCep(), f.getLogradouro(), f.getNumero(), f.getComplemento(),
                f.getBairro(), f.getCidade(), f.getUf(), f.getCriadoEm(), f.getAtualizadoEm());
    }
}
