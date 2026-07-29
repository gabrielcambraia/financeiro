package com.financeiro.service;

import com.financeiro.entity.IndiceEconomico;
import com.financeiro.repository.IndiceEconomicoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Busca e mantém em cache local (tabela {@code indices_economicos}) as
 * séries mensais públicas do SGS/Banco Central usadas para creditar
 * rendimento automático de ativos (ver {@code AgendadorRendimento}).
 *
 * <p><b>Atenção:</b> os códigos de série abaixo (CDI=4391, Selic=4390,
 * IPCA=433) foram os documentados publicamente no momento da implementação,
 * mas NÃO foram validados contra uma chamada real à API do Banco Central
 * neste ambiente (sem acesso à internet durante o desenvolvimento). Confirme
 * manualmente em https://api.bcb.gov.br/dados/serie/bcdata.sgs.{codigo}/dados?formato=json&dataInicial=01/01/2024
 * antes de considerar os valores creditados em produção confiáveis.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicoIndiceEconomico {

    public static final String CDI = "4391";
    public static final String SELIC = "4390";
    public static final String IPCA = "433";

    private static final DateTimeFormatter FORMATO_DATA_BC = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String URL_SGS = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.%s/dados?formato=json&dataInicial=%s";

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
     * salvo (ou de 5 anos atrás, se ainda não há nada em cache) e persiste
     * só os meses ainda ausentes. Falha de rede é capturada e logada — nunca
     * propagada, pois isso derrubaria o startup/agendador mensal; sem o
     * índice do mês, o rendimento daquele mês simplesmente aguarda a próxima
     * tentativa (ver AgendadorRendimento).
     */
    public void atualizarIndice(String codigo) {
        try {
            LocalDate dataInicial = repository.findByCodigo(codigo).stream()
                    .map(IndiceEconomico::getMes)
                    .max(Comparator.naturalOrder())
                    .map(mes -> YearMonth.parse(mes).plusMonths(1).atDay(1))
                    .orElse(LocalDate.now().minusYears(5).withDayOfMonth(1));

            String url = URL_SGS.formatted(codigo, dataInicial.format(FORMATO_DATA_BC));
            PontoSgs[] pontos = restClient.get().uri(url).retrieve().body(PontoSgs[].class);
            if (pontos == null) {
                return;
            }

            for (PontoSgs ponto : pontos) {
                LocalDate data = LocalDate.parse(ponto.data(), FORMATO_DATA_BC);
                String mes = YearMonth.from(data).toString();
                if (!repository.existsByCodigoAndMes(codigo, mes)) {
                    repository.save(IndiceEconomico.builder()
                            .codigo(codigo)
                            .mes(mes)
                            .valor(new BigDecimal(ponto.valor()))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao consultar/persistir índice econômico {} (Banco Central): {}", codigo, e.getMessage());
        }
    }

    public List<IndiceEconomico> listar(String codigo) {
        return repository.findByCodigo(codigo);
    }

    /** Formato de resposta da API SGS: {"data":"dd/MM/yyyy","valor":"0.85"} por ponto. */
    private record PontoSgs(String data, String valor) {
    }
}
