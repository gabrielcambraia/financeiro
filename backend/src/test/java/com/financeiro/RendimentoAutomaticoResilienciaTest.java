package com.financeiro;

import com.financeiro.entity.Ativo;
import com.financeiro.entity.Conta;
import com.financeiro.entity.MovimentacaoAtivo;
import com.financeiro.entity.enums.TipoAtivo;
import com.financeiro.entity.enums.TipoMovimentacaoAtivo;
import com.financeiro.entity.enums.TipoRemuneracao;
import com.financeiro.repository.AtivoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.MovimentacaoAtivoRepository;
import com.financeiro.scheduler.AgendadorRendimento;
import com.financeiro.service.AtivoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * Cobre o endurecimento do {@link AgendadorRendimento} contra falha isolada
 * por ativo e processamento em lotes (review_seguranca.md, itens 1 e 2).
 * {@code tamanho-lote=1} força múltiplas páginas mesmo com poucos ativos
 * semeados, provando que a paginação não pula ninguém entre páginas.
 *
 * <p>{@code @SpyBean} + {@code @TestPropertySource} forçam um
 * {@code ApplicationContext} separado do resto da suíte (o container Postgres
 * estático continua sendo reaproveitado) — por isso os dois casos ficam
 * juntos nesta classe em vez de espalhados por {@link RendimentoAutomaticoTest}.</p>
 */
@TestPropertySource(properties = "financeiro.rendimento.tamanho-lote=1")
class RendimentoAutomaticoResilienciaTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorRendimento agendador;

    @Autowired
    private AtivoRepository ativoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private MovimentacaoAtivoRepository movimentacaoRepository;

    @SpyBean
    private AtivoService ativoService;

    @Test
    void falhaEmUmAtivo_naoImpedeProcessamentoDosDemais() {
        Usuario u1 = registrarComConta(BigDecimal.valueOf(1000));
        Usuario u2 = registrarComConta(BigDecimal.valueOf(1000));
        Usuario u3 = registrarComConta(BigDecimal.valueOf(1000));
        YearMonth inicio = YearMonth.now().minusMonths(1);

        Ativo bom1 = seedAtivo(u1, inicio);
        Ativo ruim = seedAtivo(u2, inicio);
        Ativo bom2 = seedAtivo(u3, inicio);

        doThrow(new RuntimeException("falha simulada"))
                .when(ativoService)
                .creditarRendimentoAutomatico(argThat(a -> a != null && a.getId().equals(ruim.getId())), any(), any());

        agendador.onStartup();

        assertThat(movimentacoesRendimento(u1.espacoId(), bom1.getId())).isNotEmpty();
        assertThat(movimentacoesRendimento(u3.espacoId(), bom2.getId())).isNotEmpty();
        assertThat(ativoRepository.findById(bom1.getId()).orElseThrow().getRendidoAte()).isNotNull();
        assertThat(ativoRepository.findById(bom2.getId()).orElseThrow().getRendidoAte()).isNotNull();

        // o ativo com falha não avança rendidoAte — vai ser reprocessado na próxima execução
        assertThat(movimentacoesRendimento(u2.espacoId(), ruim.getId())).isEmpty();
        assertThat(ativoRepository.findById(ruim.getId()).orElseThrow().getRendidoAte()).isNull();
    }

    @Test
    void processaAlemDoTamanhoDoLote() {
        Usuario u1 = registrarComConta(BigDecimal.valueOf(1000));
        Usuario u2 = registrarComConta(BigDecimal.valueOf(1000));
        Usuario u3 = registrarComConta(BigDecimal.valueOf(1000));
        YearMonth inicio = YearMonth.now().minusMonths(1);

        Ativo a1 = seedAtivo(u1, inicio);
        Ativo a2 = seedAtivo(u2, inicio);
        Ativo a3 = seedAtivo(u3, inicio);

        agendador.onStartup();

        assertThat(movimentacoesRendimento(u1.espacoId(), a1.getId())).isNotEmpty();
        assertThat(movimentacoesRendimento(u2.espacoId(), a2.getId())).isNotEmpty();
        assertThat(movimentacoesRendimento(u3.espacoId(), a3.getId())).isNotEmpty();
    }

    // ---------- helpers ----------

    private Ativo seedAtivo(Usuario u, YearMonth inicioRendimento) {
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        Ativo ativo = Ativo.builder()
                .nome("Ativo Teste Resiliencia")
                .tipo(TipoAtivo.RENDA_FIXA)
                .conta(conta)
                .cor("#112233")
                .icone("chart")
                .valorAtual(BigDecimal.valueOf(1000))
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .remuneracaoTipo(TipoRemuneracao.PRE_FIXADA)
                .taxa(BigDecimal.valueOf(12))
                .inicioRendimento(inicioRendimento.atDay(1))
                .build();
        return ativoRepository.save(ativo);
    }

    private List<MovimentacaoAtivo> movimentacoesRendimento(Long espacoId, Long ativoId) {
        return movimentacaoRepository.findByEspacoIdAndAtivoIdOrderByDataDesc(espacoId, ativoId).stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoAtivo.RENDIMENTO)
                .toList();
    }
}
