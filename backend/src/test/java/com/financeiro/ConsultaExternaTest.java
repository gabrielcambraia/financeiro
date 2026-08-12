package com.financeiro;

import com.financeiro.dto.RespostaConsultaCep;
import com.financeiro.dto.RespostaConsultaCnpj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Testa os endpoints {@code GET /api/consultas/cnpj/{cnpj}} e
 * {@code GET /api/consultas/cep/{cep}} via HTTP completo.
 * {@link com.financeiro.service.ServicoConsultaExterna} é mockado em
 * {@link TesteIntegracaoBase} — esses testes verificam o contrato HTTP
 * (status, corpo, autenticação), não a integração com a BrasilAPI.
 */
class ConsultaExternaTest extends TesteIntegracaoBase {

    private String token;

    @BeforeEach
    void setup() {
        token = registrar();
    }

    // ──── /api/consultas/cnpj ────

    @Test
    void cnpj_valido_retorna200ComDadosEmpresa() {
        var esperado = new RespostaConsultaCnpj(
                "EMPRESA FICTÍCIA LTDA", "Empresa Fictícia",
                "contato@empresa.com", "11999998888",
                "01310100", "Av. Paulista", "1000", "Andar 5",
                "Bela Vista", "São Paulo", "SP"
        );
        when(servicoConsultaExterna.consultarCnpj("11222333000181")).thenReturn(esperado);

        ResponseEntity<RespostaConsultaCnpj> resposta =
                get("/api/consultas/cnpj/11222333000181", token, RespostaConsultaCnpj.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().razaoSocial()).isEqualTo("EMPRESA FICTÍCIA LTDA");
        assertThat(resposta.getBody().uf()).isEqualTo("SP");
        assertThat(resposta.getBody().logradouro()).isEqualTo("Av. Paulista");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void cnpj_naoEncontradoNaBrasilApi_propaga404() {
        when(servicoConsultaExterna.consultarCnpj("00000000000191"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "CNPJ não encontrado"));

        ResponseEntity<Map> resposta = get("/api/consultas/cnpj/00000000000191", token, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void cnpj_brasilApiIndisponivel_propaga502() {
        when(servicoConsultaExterna.consultarCnpj("11222333000181"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Serviço de consulta indisponível"));

        ResponseEntity<Map> resposta = get("/api/consultas/cnpj/11222333000181", token, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void cnpj_semAutenticacao_retorna401() {
        ResponseEntity<Map> resposta = restTemplate.getForEntity(
                url("/api/consultas/cnpj/11222333000181"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ──── /api/consultas/cep ────

    @Test
    void cep_valido_retorna200ComEndereco() {
        var esperado = new RespostaConsultaCep(
                "01310100", "Av. Paulista", "Bela Vista", "São Paulo", "SP");
        when(servicoConsultaExterna.consultarCep("01310100")).thenReturn(esperado);

        ResponseEntity<RespostaConsultaCep> resposta =
                get("/api/consultas/cep/01310100", token, RespostaConsultaCep.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().logradouro()).isEqualTo("Av. Paulista");
        assertThat(resposta.getBody().cidade()).isEqualTo("São Paulo");
        assertThat(resposta.getBody().uf()).isEqualTo("SP");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void cep_naoEncontrado_propaga404() {
        when(servicoConsultaExterna.consultarCep("00000000"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "CEP não encontrado"));

        ResponseEntity<Map> resposta = get("/api/consultas/cep/00000000", token, Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void cep_semAutenticacao_retorna401() {
        ResponseEntity<Map> resposta = restTemplate.getForEntity(
                url("/api/consultas/cep/01310100"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
