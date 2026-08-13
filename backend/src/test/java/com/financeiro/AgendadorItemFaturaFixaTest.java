package com.financeiro;

import com.financeiro.entity.Cartao;
import com.financeiro.entity.ItemFatura;
import com.financeiro.repository.CartaoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.ItemFaturaRepository;
import com.financeiro.repository.TransacaoRepository;
import com.financeiro.scheduler.AgendadorFatura;
import com.financeiro.scheduler.AgendadorItemFaturaFixa;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para {@link AgendadorItemFaturaFixa}. Semeia estados
 * diretamente via repositório (como AgendadorTransacaoFixaTest faz) para exercitar
 * o agendador sem depender de fluxos completos da API.
 */
class AgendadorItemFaturaFixaTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorItemFaturaFixa agendador;

    @Autowired
    private ItemFaturaRepository itemFaturaRepository;

    @Autowired
    private AgendadorFatura agendadorFatura;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CartaoRepository cartaoRepository;

    // -----------------------------------------------------------------------
    // 1. Extensão básica: agendador cria 12 meses futuros para item fixa ativo
    // -----------------------------------------------------------------------

    @Test
    void extensao_criaEntradasFixasFaltantes() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);
        Cartao cartao = cartaoRepository.findById(cartaoId).orElseThrow();

        ItemFatura cabeca = seedItemFixa(u, cartao, LocalDate.now(), BigDecimal.valueOf(50));
        // auto-referência: cabeça aponta para si mesma
        cabeca.setOrigemFixaId(cabeca.getId());
        itemFaturaRepository.save(cabeca);

        agendador.onStartup();

        YearMonth mesAtual = YearMonth.now();
        for (int i = 1; i <= 12; i++) {
            YearMonth mesAlvo = mesAtual.plusMonths(i);
            long total = itemFaturaRepository.findAll().stream()
                    .filter(item -> u.espacoId().equals(item.getEspacoId()))
                    .filter(item -> item.getOrigemFixaId() != null
                            && item.getOrigemFixaId().equals(cabeca.getId()))
                    .filter(item -> {
                        YearMonth mesDaData = YearMonth.from(item.getData());
                        return mesDaData.equals(mesAlvo);
                    })
                    .count();
            assertThat(total).as("deve existir exatamente 1 item fixa para o mês +%d", i).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // 2. Idempotência: rodar duas vezes não duplica os itens
    // -----------------------------------------------------------------------

    @Test
    void extensao_idempotente_naoDuplica() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);
        Cartao cartao = cartaoRepository.findById(cartaoId).orElseThrow();

        ItemFatura cabeca = seedItemFixa(u, cartao, LocalDate.now(), BigDecimal.valueOf(40));
        cabeca.setOrigemFixaId(cabeca.getId());
        itemFaturaRepository.save(cabeca);

        agendador.onStartup();
        long totalAposPrimeira = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId())).count();

        agendador.onFirstOfMonth();
        long totalAposSegunda = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId())).count();

        assertThat(totalAposSegunda)
                .as("segunda execução não deve criar itens extras")
                .isEqualTo(totalAposPrimeira);
    }

    // -----------------------------------------------------------------------
    // 3. Trilho encerrado (serieAtiva=false) não é estendido
    // -----------------------------------------------------------------------

    @Test
    void extensao_naoEstende_trilhoEncerrado() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);
        Cartao cartao = cartaoRepository.findById(cartaoId).orElseThrow();

        // item com série desativada
        ItemFatura cabeca = seedItemFixa(u, cartao, LocalDate.now(), BigDecimal.valueOf(60));
        cabeca.setOrigemFixaId(cabeca.getId());
        cabeca.setSerieAtiva(false); // trilho encerrado
        itemFaturaRepository.save(cabeca);

        long totalAntes = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId())).count();

        agendador.onStartup();

        long totalDepois = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId())).count();

        assertThat(totalDepois)
                .as("trilho encerrado não deve gerar novos itens")
                .isEqualTo(totalAntes);
    }

    // -----------------------------------------------------------------------
    // 4. Item criado para mês futuro herda origemFixaId e serieAtiva=true
    // -----------------------------------------------------------------------

    @Test
    void extensao_herda_origemFixaId_da_cabeca() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);
        Cartao cartao = cartaoRepository.findById(cartaoId).orElseThrow();

        ItemFatura cabeca = seedItemFixa(u, cartao, LocalDate.now(), BigDecimal.valueOf(70));
        cabeca.setOrigemFixaId(cabeca.getId());
        itemFaturaRepository.save(cabeca);

        agendador.onStartup();

        YearMonth mesSeguinte = YearMonth.now().plusMonths(1);
        ItemFatura extendido = itemFaturaRepository.findAll().stream()
                .filter(i -> u.espacoId().equals(i.getEspacoId()))
                .filter(i -> YearMonth.from(i.getData()).equals(mesSeguinte))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item do mês seguinte não encontrado"));

        assertThat(extendido.getOrigemFixaId())
                .as("item estendido deve referenciar a cabeça da série")
                .isEqualTo(cabeca.getId());
        assertThat(extendido.isSerieAtiva()).isTrue();
        assertThat(extendido.isFixa()).isTrue();
    }

    /**
     * Semeia um ItemFatura fixa diretamente via repositório, sem passar pela API,
     * para simular estados que o agendador deve processar.
     */
    private ItemFatura seedItemFixa(Usuario u, Cartao cartao, LocalDate data, BigDecimal valor) {
        ItemFatura item = ItemFatura.builder()
                .cartao(cartao)
                .valor(valor)
                .descricao("seed fixa")
                .data(data)
                .fixa(true)
                .serieAtiva(true)
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .build();
        return itemFaturaRepository.save(item);
    }
}
