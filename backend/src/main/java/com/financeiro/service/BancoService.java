package com.financeiro.service;

import com.financeiro.dto.BancoDTO;
import com.financeiro.entity.Banco;
import com.financeiro.erro.ExcecaoRecursoNaoEncontrado;
import com.financeiro.repository.BancoRepository;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.ContaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BancoService {

    private static final Set<String> TIPOS_IMAGEM_PERMITIDOS = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long TAMANHO_MAXIMO_BYTES = 1_000_000; // 1MB — guardado como bytea no Postgres

    private final BancoRepository repository;
    private final ContaRepository contaRepository;
    private final CartaoRepository cartaoRepository;

    public List<BancoDTO> findAll() {
        return repository.findAllOrdenado().stream().map(this::toDTO).toList();
    }

    public Banco buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ExcecaoRecursoNaoEncontrado("Banco não encontrado: " + id));
    }

    public BancoDTO create(BancoDTO dto) {
        Banco banco = Banco.builder()
                .nome(dto.getNome())
                .corPrimaria(dto.getCorPrimaria())
                .sigla(dto.getSigla())
                .build();
        return toDTO(repository.save(banco));
    }

    public BancoDTO update(Long id, BancoDTO dto) {
        Banco banco = buscarEntidade(id);
        banco.setNome(dto.getNome());
        banco.setCorPrimaria(dto.getCorPrimaria());
        banco.setSigla(dto.getSigla());
        return toDTO(repository.save(banco));
    }

    public void delete(Long id) {
        Banco banco = buscarEntidade(id);
        if (contaRepository.existsByBancoId(id) || cartaoRepository.existsByBancoId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este banco está em uso por contas ou cartões e não pode ser excluído");
        }
        repository.delete(banco);
    }

    @Transactional
    public BancoDTO uploadLogo(Long id, byte[] bytes, String contentType) {
        if (contentType == null || !TIPOS_IMAGEM_PERMITIDOS.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de imagem não suportado. Use PNG, JPEG ou WEBP.");
        }
        if (bytes.length > TAMANHO_MAXIMO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem muito grande (máximo 1MB)");
        }
        Banco banco = buscarEntidade(id);
        banco.setLogo(bytes);
        banco.setLogoTipo(contentType);
        return toDTO(repository.save(banco));
    }

    public BancoDTO removerLogo(Long id) {
        Banco banco = buscarEntidade(id);
        banco.setLogo(null);
        banco.setLogoTipo(null);
        return toDTO(repository.save(banco));
    }

    public BancoDTO toDTO(Banco b) {
        BancoDTO dto = new BancoDTO();
        dto.setId(b.getId());
        dto.setNome(b.getNome());
        dto.setCorPrimaria(b.getCorPrimaria());
        dto.setSigla(b.getSigla());
        dto.setTemLogo(b.getLogo() != null);
        return dto;
    }
}
