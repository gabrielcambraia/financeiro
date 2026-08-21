package com.financeiro.service;

import com.financeiro.dto.RespostaConfiguracaoPlataforma;
import com.financeiro.entity.ConfiguracaoPlataforma;
import com.financeiro.repository.ConfiguracaoPlataformaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConfiguracaoPlataformaService {

    private static final Set<String> TIPOS_IMAGEM_PERMITIDOS = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long TAMANHO_MAXIMO_BYTES = 1_000_000; // 1MB — guardado como bytea no Postgres

    private final ConfiguracaoPlataformaRepository repository;

    public ConfiguracaoPlataforma buscarEntidade() {
        return repository.findById(ConfiguracaoPlataforma.ID_UNICO)
                .orElseGet(() -> {
                    ConfiguracaoPlataforma nova = new ConfiguracaoPlataforma();
                    nova.setId(ConfiguracaoPlataforma.ID_UNICO);
                    return repository.save(nova);
                });
    }

    public RespostaConfiguracaoPlataforma obterInfo() {
        return toDTO(buscarEntidade());
    }

    public RespostaConfiguracaoPlataforma uploadLogo(byte[] bytes, String contentType) {
        validar(bytes, contentType);
        ConfiguracaoPlataforma configuracao = buscarEntidade();
        configuracao.setLogo(bytes);
        configuracao.setLogoTipo(contentType);
        return toDTO(repository.save(configuracao));
    }

    public RespostaConfiguracaoPlataforma removerLogo() {
        ConfiguracaoPlataforma configuracao = buscarEntidade();
        configuracao.setLogo(null);
        configuracao.setLogoTipo(null);
        return toDTO(repository.save(configuracao));
    }

    // Banner exibido na tela de login, independente da logo da barra lateral/favicon.
    public RespostaConfiguracaoPlataforma uploadLogoLogin(byte[] bytes, String contentType) {
        validar(bytes, contentType);
        ConfiguracaoPlataforma configuracao = buscarEntidade();
        configuracao.setLogoLogin(bytes);
        configuracao.setLogoLoginTipo(contentType);
        return toDTO(repository.save(configuracao));
    }

    public RespostaConfiguracaoPlataforma removerLogoLogin() {
        ConfiguracaoPlataforma configuracao = buscarEntidade();
        configuracao.setLogoLogin(null);
        configuracao.setLogoLoginTipo(null);
        return toDTO(repository.save(configuracao));
    }

    private void validar(byte[] bytes, String contentType) {
        if (contentType == null || !TIPOS_IMAGEM_PERMITIDOS.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de imagem não suportado. Use PNG, JPEG ou WEBP.");
        }
        if (bytes.length > TAMANHO_MAXIMO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem muito grande (máximo 1MB)");
        }
    }

    private RespostaConfiguracaoPlataforma toDTO(ConfiguracaoPlataforma configuracao) {
        return new RespostaConfiguracaoPlataforma(configuracao.getLogo() != null, configuracao.getLogoLogin() != null);
    }
}
