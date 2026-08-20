package com.financeiro;

import com.financeiro.entity.IndiceEconomico;
import com.financeiro.repository.IndiceEconomicoRepository;
import com.financeiro.service.ServicoIndiceEconomico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Testa {@link ServicoIndiceEconomico} isoladamente (sem contexto Spring),
 * com {@link MockRestServiceServer} no lugar da API real do SGS/Banco Central
 * e {@link IndiceEconomicoRepository} mockado. Ponto central: o serviço nunca
 * deve buscar/persistir o mês corrente das séries do BCB — ele é publicado
 * com valor ACUMULADO PARCIAL, que ficaria congelado para sempre (ver Javadoc
 * de {@link ServicoIndiceEconomico}).
 */
class ServicoIndiceEconomicoTest {

    private static final DateTimeFormatter FORMATO_DATA_BC = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private IndiceEconomicoRepository repository;
    private MockRestServiceServer servidorFalso;
    private ServicoIndiceEconomico servico;

    @BeforeEach
    void montarServico() {
        repository = mock(IndiceEconomicoRepository.class);
        RestClient.Builder construtor = RestClient.builder();
        servidorFalso = MockRestServiceServer.bindTo(construtor).build();
        servico = new ServicoIndiceEconomico(construtor.build(), repository);
    }

    @Test
    void naoPersisteMesCorrenteParcial() {
        YearMonth ultimoMesFechado = YearMonth.now().minusMonths(1);
        YearMonth mesAnterior = ultimoMesFechado.minusMonths(1);
        YearMonth mesCorrente = ultimoMesFechado.plusMonths(1);

        when(repository.findTopByCodigoOrderByMesDesc("4391"))
                .thenReturn(Optional.of(IndiceEconomico.builder().codigo("4391").mes(mesAnterior.toString()).valor(BigDecimal.ONE).build()));

        String corpo = """
                [{"data":"%s","valor":"1.15"},{"data":"%s","valor":"0.30"}]
                """.formatted(
                ultimoMesFechado.atDay(1).format(FORMATO_DATA_BC),
                mesCorrente.atDay(1).format(FORMATO_DATA_BC));

        servidorFalso.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.bcb.gov.br/dados/serie/bcdata.sgs.4391/dados")))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

        servico.atualizarIndice("4391");

        // Só o mês fechado é gravado; o ponto do mês corrente (mesmo vindo na
        // resposta) é descartado pela guarda defensiva de mesAlvo.
        verify(repository, times(1)).save(any());
        verify(repository).save(argThat(indice ->
                indice.getMes().equals(ultimoMesFechado.toString())
                        && indice.getValor().compareTo(new BigDecimal("1.15")) == 0));
        servidorFalso.verify();
    }

    @Test
    void naoChamaApiQuandoJaTemUltimoMesFechado() {
        YearMonth ultimoMesFechado = YearMonth.now().minusMonths(1);
        when(repository.findTopByCodigoOrderByMesDesc("433"))
                .thenReturn(Optional.of(IndiceEconomico.builder().codigo("433").mes(ultimoMesFechado.toString()).valor(BigDecimal.ONE).build()));

        servico.atualizarIndice("433");

        servidorFalso.verify(); // nenhuma expectativa registrada + verify() prova zero requisições
        verify(repository, never()).save(any());
    }

    @Test
    void enviaDataInicialEDataFinalDoUltimoMesFechado() {
        YearMonth mesAnterior = YearMonth.now().minusMonths(2);
        YearMonth ultimoMesFechado = YearMonth.now().minusMonths(1);
        when(repository.findTopByCodigoOrderByMesDesc("4390"))
                .thenReturn(Optional.of(IndiceEconomico.builder().codigo("4390").mes(mesAnterior.toString()).valor(BigDecimal.ONE).build()));

        LocalDate dataInicialEsperada = mesAnterior.plusMonths(1).atDay(1);
        LocalDate dataFinalEsperada = ultimoMesFechado.atEndOfMonth();
        String urlEsperada = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.4390/dados?formato=json&dataInicial=%s&dataFinal=%s"
                .formatted(dataInicialEsperada.format(FORMATO_DATA_BC), dataFinalEsperada.format(FORMATO_DATA_BC));

        servidorFalso.expect(requestTo(urlEsperada)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        servico.atualizarIndice("4390");

        servidorFalso.verify();
    }

    @Test
    void semDadoNovoNaoLancaNemPersiste() {
        when(repository.findTopByCodigoOrderByMesDesc("433")).thenReturn(Optional.empty());

        servidorFalso.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"erro\":{\"statusCode\":404,\"detail\":\"Value(s) not found\"}}"));

        servico.atualizarIndice("433"); // não deve lançar

        verify(repository, never()).save(any());
    }

    @Test
    void falhaDeServidorNaoPropaga() {
        when(repository.findTopByCodigoOrderByMesDesc("4391")).thenReturn(Optional.empty());

        servidorFalso.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.bcb.gov.br/dados/serie/bcdata.sgs.4391/dados")))
                .andRespond(withServerError());

        servico.atualizarIndice("4391"); // não deve lançar

        verify(repository, never()).save(any());
    }

    @Test
    void jsonInvalidoNaoPropaga() {
        when(repository.findTopByCodigoOrderByMesDesc("433")).thenReturn(Optional.empty());

        servidorFalso.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados")))
                .andRespond(withSuccess("não é json", MediaType.APPLICATION_JSON));

        servico.atualizarIndice("433"); // não deve lançar
    }

    @Test
    void pontoComValorNuloOuVazioEIgnorado() {
        YearMonth ultimoMesFechado = YearMonth.now().minusMonths(1);
        when(repository.findTopByCodigoOrderByMesDesc("433")).thenReturn(Optional.empty());

        String corpo = """
                [{"data":"%s","valor":null},{"data":"%s","valor":""}]
                """.formatted(
                ultimoMesFechado.atDay(1).format(FORMATO_DATA_BC),
                ultimoMesFechado.atDay(1).format(FORMATO_DATA_BC));

        servidorFalso.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.bcb.gov.br/dados/serie/bcdata.sgs.433/dados")))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

        servico.atualizarIndice("433");

        verify(repository, never()).save(any());
    }

    @Test
    void backfillUsaCincoAnosQuandoCacheVazio() {
        when(repository.findTopByCodigoOrderByMesDesc("433")).thenReturn(Optional.empty());

        LocalDate dataInicialEsperada = LocalDate.now().minusYears(5).withDayOfMonth(1);
        servidorFalso.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "dataInicial=" + dataInicialEsperada.format(FORMATO_DATA_BC))))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        servico.atualizarIndice("433");

        servidorFalso.verify();
    }
}
