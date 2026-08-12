package com.financeiro.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.financeiro.dto.RespostaConsultaCep;
import com.financeiro.dto.RespostaConsultaCnpj;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServicoConsultaExterna {

    private static final String URL_CNPJ = "https://brasilapi.com.br/api/cnpj/v1/%s";
    private static final String URL_CEP  = "https://brasilapi.com.br/api/cep/v2/%s";

    private final RestClient restClient;

    public RespostaConsultaCnpj consultarCnpj(String cnpj) {
        String limpo = cnpj.replaceAll("[.\\-/]", "").toUpperCase();
        if (limpo.length() != 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ inválido");
        }
        try {
            CnpjBrasilApi resp = restClient.get()
                    .uri(URL_CNPJ.formatted(limpo))
                    .retrieve()
                    .body(CnpjBrasilApi.class);
            if (resp == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CNPJ não encontrado");
            }
            String telefone = resp.ddd_telefone_1() != null && !resp.ddd_telefone_1().isBlank()
                    ? resp.ddd_telefone_1().replaceAll("\\D", "")
                    : null;
            return new RespostaConsultaCnpj(
                    resp.razao_social(),
                    resp.nome_fantasia(),
                    resp.email(),
                    telefone,
                    resp.cep(),
                    resp.logradouro(),
                    resp.numero(),
                    resp.complemento(),
                    resp.bairro(),
                    resp.municipio(),
                    resp.uf()
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CNPJ não encontrado");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao consultar CNPJ {} na BrasilAPI: {}", limpo, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Serviço de consulta indisponível");
        }
    }

    public RespostaConsultaCep consultarCep(String cep) {
        String limpo = cep.replaceAll("\\D", "");
        if (limpo.length() != 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CEP inválido");
        }
        try {
            CepBrasilApi resp = restClient.get()
                    .uri(URL_CEP.formatted(limpo))
                    .retrieve()
                    .body(CepBrasilApi.class);
            if (resp == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CEP não encontrado");
            }
            return new RespostaConsultaCep(
                    resp.cep(),
                    resp.street(),
                    resp.neighborhood(),
                    resp.city(),
                    resp.state()
            );
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CEP não encontrado");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Falha ao consultar CEP {} na BrasilAPI: {}", limpo, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Serviço de consulta indisponível");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CnpjBrasilApi(
            String razao_social,
            String nome_fantasia,
            String email,
            String ddd_telefone_1,
            String cep,
            String logradouro,
            String numero,
            String complemento,
            String bairro,
            String municipio,
            String uf
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CepBrasilApi(
            String cep,
            String state,
            String city,
            String neighborhood,
            String street
    ) {}
}
