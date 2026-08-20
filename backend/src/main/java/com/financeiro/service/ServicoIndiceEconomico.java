package com.financeiro.service;

import com.financeiro.entity.IndiceEconomico;
import com.financeiro.repository.IndiceEconomicoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Busca e mantém em cache local (tabela {@code indices_economicos}) as
 * séries mensais públicas do SGS/Banco Central usadas para creditar
 * rendimento automático de ativos (ver {@code AgendadorRendimento}).
 *
 * <p>Códigos de série validados contra a API real do SGS (CDI=4391, Selic=4390,
 * IPCA=433): os três respondem no formato {@code {"data":"dd/MM/yyyy","valor":"X.XX"}}
 * esperado pelo parser, com valores mensais percentuais em faixas condizentes
 * (CDI/Selic ~1,1–1,2%/mês, IPCA ~0,16–0,58%/mês).</p>
 *
 * <p><b>Importante:</b> as séries 4391 (CDI) e 4390 (Selic) publicam o mês
 * corrente com o valor ACUMULADO PARCIAL do mês em curso, que cresce a cada
 * dia útil até o fechamento. Por isso este serviço nunca busca/persiste além
 * do último mês fechado ({@link YearMonth#minusMonths(long) hoje - 1 mês}) —
 * gravar o parcial congelaria um valor errado que a persistência (insere só
 * se o mês ainda estiver ausente) nunca corrigiria depois. O IPCA (433) não
 * sofre disso: o SGS só publica esta série já fechada.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicoIndiceEconomico {

    public static final String CDI = "4391";
    public static final String SELIC = "4390";
    public static final String IPCA = "433";

    /**
     * Dias de tolerância após o fim do mês-alvo antes de tratar a ausência de
     * publicação como problema (WARN) em vez de estado normal (DEBUG). CDI/Selic
     * fecham no 1º dia útil do mês seguinte, IPCA por volta do dia 10 — 20 dias
     * cobre os três com folga sem precisar de um limiar por série.
     */
    private static final int DIAS_TOLERANCIA_PUBLICACAO = 20;

    private static final DateTimeFormatter FORMATO_DATA_BC = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String URL_SGS =
            "https://api.bcb.gov.br/dados/serie/bcdata.sgs.%s/dados?formato=json&dataInicial=%s&dataFinal=%s";

    private final RestClient restClient;
    private final IndiceEconomicoRepository repository;

    /** Atualiza as três séries usadas hoje (CDI, Selic, IPCA). Nunca lança exceção. */
    public void atualizarTodos() {
        atualizarIndice(CDI);
        atualizarIndice(SELIC);
        atualizarIndice(IPCA);
    }

    /**
     * Busca do SGS apenas os pontos a partir do mês seguinte ao último já
     * salvo (ou de 5 anos atrás, se ainda não há nada em cache) até o último
     * mês fechado, e persiste só os meses ainda ausentes. Falha de rede é
     * capturada e logada — nunca propagada, pois isso derrubaria o
     * startup/agendador diário; sem o índice do mês, o rendimento daquele mês
     * simplesmente aguarda a próxima tentativa (ver AgendadorRendimento).
     */
    public void atualizarIndice(String codigo) {
        try {
            YearMonth mesAlvo = YearMonth.now().minusMonths(1);
            LocalDate dataInicial = repository.findTopByCodigoOrderByMesDesc(codigo)
                    .map(indice -> YearMonth.parse(indice.getMes()).plusMonths(1).atDay(1))
                    .orElse(LocalDate.now().minusYears(5).withDayOfMonth(1));
            LocalDate dataFinal = mesAlvo.atEndOfMonth();

            if (dataInicial.isAfter(dataFinal)) {
                log.debug("Índice econômico {} já está em dia até {} — nada a buscar", codigo, mesAlvo);
                return;
            }

            String url = URL_SGS.formatted(codigo, dataInicial.format(FORMATO_DATA_BC), dataFinal.format(FORMATO_DATA_BC));
            PontoSgs[] pontos = restClient.get().uri(url).retrieve().body(PontoSgs[].class);
            if (pontos == null) {
                return;
            }

            for (PontoSgs ponto : pontos) {
                if (ponto.valor() == null || ponto.valor().isBlank()) {
                    continue;
                }
                LocalDate data = LocalDate.parse(ponto.data(), FORMATO_DATA_BC);
                YearMonth mesPonto = YearMonth.from(data);
                if (mesPonto.isAfter(mesAlvo)) {
                    continue; // cinto e suspensório: dataFinal já deveria impedir isso
                }
                String mes = mesPonto.toString();
                if (!repository.existsByCodigoAndMes(codigo, mes)) {
                    repository.save(IndiceEconomico.builder()
                            .codigo(codigo)
                            .mes(mes)
                            .valor(new BigDecimal(ponto.valor()))
                            .build());
                }
            }
        } catch (HttpClientErrorException.NotFound e) {
            registrarAusenciaDeDado(codigo, YearMonth.now().minusMonths(1));
        } catch (Exception e) {
            log.warn("Falha ao consultar/persistir índice econômico {} (Banco Central): {}", codigo, e.getMessage());
        }
    }

    /**
     * O SGS responde 404 quando não há dado no intervalo pedido — o que é o
     * estado normal logo após o mês-alvo já estar em cache (a próxima
     * requisição pede o mês seguinte, ainda não publicado). Só vira WARN se a
     * ausência persistir além da janela normal de publicação.
     */
    private void registrarAusenciaDeDado(String codigo, YearMonth mesAlvo) {
        LocalDate prazoPublicacao = mesAlvo.atEndOfMonth().plusDays(DIAS_TOLERANCIA_PUBLICACAO);
        if (LocalDate.now().isAfter(prazoPublicacao)) {
            log.warn("Índice econômico {} sem publicação do mês {} há mais de {} dias — verificar SGS/Banco Central",
                    codigo, mesAlvo, DIAS_TOLERANCIA_PUBLICACAO);
        } else {
            log.debug("Índice econômico {} do mês {} ainda não publicado pelo Banco Central", codigo, mesAlvo);
        }
    }

    public List<IndiceEconomico> listar(String codigo) {
        return repository.findByCodigo(codigo);
    }

    /** Formato de resposta da API SGS: {"data":"dd/MM/yyyy","valor":"0.85"} por ponto. */
    private record PontoSgs(String data, String valor) {
    }
}
