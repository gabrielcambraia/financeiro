package com.financeiro;

import com.financeiro.entity.Ativo;
import com.financeiro.entity.Conta;
import com.financeiro.entity.IndiceEconomico;
import com.financeiro.entity.MovimentacaoAtivo;
import com.financeiro.entity.enums.TipoAtivo;
import com.financeiro.entity.enums.TipoMovimentacaoAtivo;
import com.financeiro.entity.enums.TipoRemuneracao;
import com.financeiro.repository.AtivoRepository;
import com.financeiro.repository.ContaRepository;
import com.financeiro.repository.IndiceEconomicoRepository;
import com.financeiro.repository.MovimentacaoAtivoRepository;
import com.financeiro.scheduler.AgendadorRendimento;
import com.financeiro.service.ServicoIndiceEconomico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link AgendadorRendimento} — crédito automático de rendimento
 * (PRE_FIXADA/POS_CDI/POS_SELIC/IPCA_MAIS) de ativos com indexador
 * configurado. Semeia {@code indices_economicos} direto no repositório (nunca
 * bate na API real do Banco Central nos testes) e ativos direto no
 * repositório, para controlar {@code rendidoAte}/{@code inicioRendimento}
 * sem depender só do fluxo HTTP de aportar.
 */
class RendimentoAutomaticoTest extends TesteIntegracaoBase {

    @Autowired
    private AgendadorRendimento agendador;

    @Autowired
    private AtivoRepository ativoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private MovimentacaoAtivoRepository movimentacaoRepository;

    @Autowired
    private IndiceEconomicoRepository indiceRepository;

    // servicoIndiceEconomico (mockado — no-op — em TesteIntegracaoBase) garante que
    // o agendador nunca bate na rede real. indices_economicos é global (sem
    // espaco_id, ver V15): usamos seedIndice (upsert) em vez de save() cru para
    // nunca colidir com uma constraint única, mesmo que outro teste desta classe já
    // tenha inserido o mesmo (codigo, mes).

    @Test
    void preFixada_creditaExatamenteUmRendimentoPorMesFechado() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        YearMonth inicio = YearMonth.now().minusMonths(2);
        Ativo ativo = seedAtivo(u, TipoRemuneracao.PRE_FIXADA, BigDecimal.valueOf(12), inicio.atDay(1), null, BigDecimal.valueOf(1000));

        agendador.onStartup();

        List<MovimentacaoAtivo> movimentos = movimentacoesRendimento(u.espacoId(), ativo.getId());
        assertThat(movimentos).hasSize(2); // mes-2 e mes-1 (mes atual não é "fechado")
        assertThat(movimentos).allMatch(m -> m.getValor().compareTo(BigDecimal.ZERO) > 0);

        Ativo recarregado = ativoRepository.findById(ativo.getId()).orElseThrow();
        assertThat(recarregado.getRendidoAte()).isEqualTo(YearMonth.now().minusMonths(1).atEndOfMonth());
        assertThat(recarregado.getValorAtual()).isGreaterThan(BigDecimal.valueOf(1000));
    }

    @Test
    void rodarDuasVezes_naoDuplicaLancamento() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        YearMonth inicio = YearMonth.now().minusMonths(2);
        Ativo ativo = seedAtivo(u, TipoRemuneracao.PRE_FIXADA, BigDecimal.valueOf(12), inicio.atDay(1), null, BigDecimal.valueOf(1000));

        agendador.onStartup();
        int totalAposPrimeiraRodada = movimentacoesRendimento(u.espacoId(), ativo.getId()).size();

        agendador.onFirstOfMonth();
        int totalAposSegundaRodada = movimentacoesRendimento(u.espacoId(), ativo.getId()).size();

        assertThat(totalAposSegundaRodada).isEqualTo(totalAposPrimeiraRodada);
    }

    @Test
    void semIndiceCadastrado_naoCreditaENaoAvancaRendidoAte() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        YearMonth inicio = YearMonth.now().minusMonths(2);
        // indices_economicos é global/sem espaco_id (V15) — garante a precondição
        // "sem índice cadastrado para este mês" mesmo que outro teste desta classe
        // já tenha semeado o mesmo mês, em vez de só assumir tabela vazia.
        removerIndiceSeExistir(ServicoIndiceEconomico.CDI, inicio);
        // POS_CDI sem nenhum índice em indices_economicos — o agendador não deve
        // encontrar o CDI do mês e precisa parar sem avançar rendidoAte.
        Ativo ativo = seedAtivo(u, TipoRemuneracao.POS_CDI, BigDecimal.valueOf(110), inicio.atDay(1), null, BigDecimal.valueOf(1000));

        agendador.onStartup();

        assertThat(movimentacoesRendimento(u.espacoId(), ativo.getId())).isEmpty();
        Ativo recarregado = ativoRepository.findById(ativo.getId()).orElseThrow();
        assertThat(recarregado.getRendidoAte()).isNull();
    }

    @Test
    void comIndiceParcial_processaSoOMesDisponivelEParaNoMesFaltante() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        YearMonth mesMaisAntigo = YearMonth.now().minusMonths(2);
        YearMonth mesIntermediario = YearMonth.now().minusMonths(1);
        Ativo ativo = seedAtivo(u, TipoRemuneracao.POS_CDI, BigDecimal.valueOf(110), mesMaisAntigo.atDay(1), null, BigDecimal.valueOf(1000));

        // só o mês mais antigo tem índice cadastrado — falta o intermediário
        removerIndiceSeExistir(ServicoIndiceEconomico.CDI, mesIntermediario);
        seedIndice(ServicoIndiceEconomico.CDI, mesMaisAntigo, BigDecimal.valueOf(0.85));

        agendador.onStartup();

        List<MovimentacaoAtivo> movimentos = movimentacoesRendimento(u.espacoId(), ativo.getId());
        assertThat(movimentos).hasSize(1);
        Ativo recarregado = ativoRepository.findById(ativo.getId()).orElseThrow();
        assertThat(recarregado.getRendidoAte()).isEqualTo(mesMaisAntigo.atEndOfMonth());

        // completando o índice que faltava e rodando de novo, o mês intermediário é processado
        seedIndice(ServicoIndiceEconomico.CDI, mesIntermediario, BigDecimal.valueOf(0.9));
        agendador.onFirstOfMonth();

        List<MovimentacaoAtivo> movimentosApos = movimentacoesRendimento(u.espacoId(), ativo.getId());
        assertThat(movimentosApos).hasSize(2);
        Ativo recarregadoApos = ativoRepository.findById(ativo.getId()).orElseThrow();
        assertThat(recarregadoApos.getRendidoAte()).isEqualTo(mesIntermediario.atEndOfMonth());
    }

    @Test
    void ativoCancelado_ignoradoPeloAgendador() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        YearMonth inicio = YearMonth.now().minusMonths(2);
        Ativo ativo = seedAtivo(u, TipoRemuneracao.PRE_FIXADA, BigDecimal.valueOf(12), inicio.atDay(1), LocalDate.now(), BigDecimal.valueOf(1000));

        agendador.onStartup();

        assertThat(movimentacoesRendimento(u.espacoId(), ativo.getId())).isEmpty();
    }

    @Test
    void isolamentoEntreEspacos_naoMisturaCredito() {
        Usuario a = registrarComConta(BigDecimal.valueOf(1000));
        Usuario b = registrarComConta(BigDecimal.valueOf(5000));
        YearMonth inicio = YearMonth.now().minusMonths(1);
        Ativo ativoA = seedAtivo(a, TipoRemuneracao.PRE_FIXADA, BigDecimal.valueOf(12), inicio.atDay(1), null, BigDecimal.valueOf(1000));
        Ativo ativoB = seedAtivo(b, TipoRemuneracao.PRE_FIXADA, BigDecimal.valueOf(24), inicio.atDay(1), null, BigDecimal.valueOf(5000));

        agendador.onStartup();

        List<MovimentacaoAtivo> movimentosA = movimentacoesRendimento(a.espacoId(), ativoA.getId());
        List<MovimentacaoAtivo> movimentosB = movimentacoesRendimento(b.espacoId(), ativoB.getId());
        assertThat(movimentosA).hasSize(1);
        assertThat(movimentosB).hasSize(1);
        assertThat(movimentosA.get(0).getEspacoId()).isEqualTo(a.espacoId());
        assertThat(movimentosB.get(0).getEspacoId()).isEqualTo(b.espacoId());

        Ativo recarregadoA = ativoRepository.findById(ativoA.getId()).orElseThrow();
        Ativo recarregadoB = ativoRepository.findById(ativoB.getId()).orElseThrow();
        // taxa maior em B deve render proporcionalmente mais que A sobre a mesma base relativa
        BigDecimal rendimentoA = recarregadoA.getValorAtual().subtract(BigDecimal.valueOf(1000));
        BigDecimal rendimentoB = recarregadoB.getValorAtual().subtract(BigDecimal.valueOf(5000));
        assertThat(rendimentoA).isGreaterThan(BigDecimal.ZERO);
        assertThat(rendimentoB).isGreaterThan(BigDecimal.ZERO);
    }

    // ---------- helpers ----------

    private Ativo seedAtivo(Usuario u, TipoRemuneracao tipo, BigDecimal taxa, LocalDate inicioRendimento,
                             LocalDate dataCancelamento, BigDecimal valorAtual) {
        Conta conta = contaRepository.findById(u.contaId()).orElseThrow();
        Ativo ativo = Ativo.builder()
                .nome("Ativo Teste Rendimento")
                .tipo(TipoAtivo.RENDA_FIXA)
                .conta(conta)
                .cor("#112233")
                .icone("chart")
                .valorAtual(valorAtual)
                .espacoId(u.espacoId())
                .usuarioId(u.usuarioId())
                .remuneracaoTipo(tipo)
                .taxa(taxa)
                .inicioRendimento(inicioRendimento)
                .dataCancelamento(dataCancelamento)
                .build();
        return ativoRepository.save(ativo);
    }

    private List<MovimentacaoAtivo> movimentacoesRendimento(Long espacoId, Long ativoId) {
        return movimentacaoRepository.findByEspacoIdAndAtivoIdOrderByDataDesc(espacoId, ativoId).stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoAtivo.RENDIMENTO)
                .toList();
    }

    /** Upsert em indices_economicos — evita violar a constraint única se o (codigo, mes) já existir. */
    private void seedIndice(String codigo, YearMonth mes, BigDecimal valor) {
        IndiceEconomico indice = indiceRepository.findByCodigoAndMes(codigo, mes.toString())
                .orElseGet(() -> IndiceEconomico.builder().codigo(codigo).mes(mes.toString()).build());
        indice.setValor(valor);
        indiceRepository.save(indice);
    }

    private void removerIndiceSeExistir(String codigo, YearMonth mes) {
        indiceRepository.findByCodigoAndMes(codigo, mes.toString()).ifPresent(indiceRepository::delete);
    }
}
