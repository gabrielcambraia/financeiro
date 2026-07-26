package com.financeiro;

import com.financeiro.dto.AtivoDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.MovimentacaoAtivoDTO;
import com.financeiro.dto.PatrimonioDTO;
import com.financeiro.entity.enums.TipoAtivo;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code AtivoService} — endpoints {@code /api/ativos}. O ponto mais
 * sensível é a assimetria entre aportar/resgatar (mexem em saldo de conta e
 * geram {@code MovimentacaoAtivo} ligada a uma {@code Transacao}) e
 * rendimento (só move {@code valorAtual} do ativo, nunca toca conta nenhuma).
 */
class InvestimentoTest extends TesteIntegracaoBase {

    @Test
    void aportar_debitaConta_criaMovimentacaoAporte() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId, TipoAtivo.RESERVA);

        AtivoDTO atualizado = aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(200));

        assertThat(atualizado.getValorAtual()).isEqualByComparingTo("200");
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("800");
        List<MovimentacaoAtivoDTO> movs = movimentacoes(token, ativo.getId());
        assertThat(movs).hasSize(1);
        assertThat(movs.get(0).getTipo().name()).isEqualTo("APORTE");
        assertThat(movs.get(0).getContaId()).isEqualTo(contaId);
    }

    @Test
    void resgatar_creditaConta_criaMovimentacaoResgate() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId, TipoAtivo.RESERVA);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(200));

        AtivoDTO atualizado = resgatar(token, ativo.getId(), contaId, BigDecimal.valueOf(80));

        assertThat(atualizado.getValorAtual()).isEqualByComparingTo("120");
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("880"); // 800 + 80
        List<MovimentacaoAtivoDTO> movs = movimentacoes(token, ativo.getId());
        assertThat(movs).anyMatch(m -> m.getTipo().name().equals("RESGATE"));
    }

    @Test
    void rendimento_naoCriaTransacao_naoTocaSaldo_contaIdNaoExigido() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId, TipoAtivo.RENDA_VARIAVEL);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(200));
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("800");

        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setValor(BigDecimal.valueOf(15));
        dto.setData(LocalDate.now());
        // contaId propositalmente ausente — rendimento não exige conta

        ResponseEntity<AtivoDTO> resposta = patch("/api/ativos/" + ativo.getId() + "/rendimento", dto, token, AtivoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().getValorAtual()).isEqualByComparingTo("215");

        // saldo de conta intacto — rendimento é ganho contábil do ativo, não movimenta dinheiro
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("800");

        List<MovimentacaoAtivoDTO> movs = movimentacoes(token, ativo.getId());
        MovimentacaoAtivoDTO movRendimento = movs.stream().filter(m -> m.getTipo().name().equals("RENDIMENTO")).findFirst().orElseThrow();
        assertThat(movRendimento.getContaId()).isNull();
    }

    @Test
    void aportarOuResgatar_semContaId_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId, TipoAtivo.RESERVA);
        // resgatar() valida "valor > investido" antes de exigir a conta — precisa
        // ter saldo investido suficiente pra alcançar a validação de conta obrigatória.
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(50));

        MovimentacaoAtivoDTO semConta = new MovimentacaoAtivoDTO();
        semConta.setValor(BigDecimal.valueOf(50));
        semConta.setData(LocalDate.now());

        ResponseEntity<Map> respostaAporte = patchComCorpoDeErro("/api/ativos/" + ativo.getId() + "/aportar", semConta, token);
        assertThat(respostaAporte.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaAporte.getBody().get("mensagem")).isEqualTo("Conta é obrigatória");

        ResponseEntity<Map> respostaResgate = patchComCorpoDeErro("/api/ativos/" + ativo.getId() + "/resgatar", semConta, token);
        assertThat(respostaResgate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaResgate.getBody().get("mensagem")).isEqualTo("Conta é obrigatória");
    }

    @Test
    void resgatar_acimaDoInvestido_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId, TipoAtivo.RESERVA);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(50));

        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setContaId(contaId);
        dto.setValor(BigDecimal.valueOf(51));
        dto.setData(LocalDate.now());

        ResponseEntity<Map> resposta = patchComCorpoDeErro("/api/ativos/" + ativo.getId() + "/resgatar", dto, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Valor maior que o investido no ativo");
    }

    @Test
    void delete_comValorAtualPositivo_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId, TipoAtivo.RESERVA);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(50));

        ResponseEntity<Map> resposta = deleteComCorpo("/api/ativos/" + ativo.getId(), token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Resgate o valor investido antes de excluir o ativo");
    }

    @Test
    void ativoCancelado_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId, TipoAtivo.RESERVA);

        ResponseEntity<AtivoDTO> respostaCancelar = patch("/api/ativos/" + ativo.getId() + "/cancelar", null, token, AtivoDTO.class);
        assertThat(respostaCancelar.getStatusCode()).isEqualTo(HttpStatus.OK);

        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setContaId(contaId);
        dto.setValor(BigDecimal.valueOf(10));
        dto.setData(LocalDate.now());

        ResponseEntity<Map> resposta = patchComCorpoDeErro("/api/ativos/" + ativo.getId() + "/aportar", dto, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Ativo cancelado");
    }

    @Test
    void findAll_calculaPercentualCarteira_proporcionalAoTotal_foraDoFindAllVemNull() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(10000));
        AtivoDTO ativoA = criarAtivo(token, contaId, TipoAtivo.RESERVA);
        AtivoDTO ativoB = criarAtivo(token, contaId, TipoAtivo.RENDA_FIXA);
        aportar(token, ativoA.getId(), contaId, BigDecimal.valueOf(300));
        aportar(token, ativoB.getId(), contaId, BigDecimal.valueOf(100));

        List<AtivoDTO> ativos = listarAtivos(token);
        AtivoDTO a = ativos.stream().filter(a1 -> a1.getId().equals(ativoA.getId())).findFirst().orElseThrow();
        AtivoDTO b = ativos.stream().filter(a1 -> a1.getId().equals(ativoB.getId())).findFirst().orElseThrow();
        assertThat(a.getPercentualCarteira()).isEqualTo(75.0);
        assertThat(b.getPercentualCarteira()).isEqualTo(25.0);

        // create() não passa pelo total da carteira -> percentualCarteira fica no default (0)
        AtivoDTO recemCriado = criarAtivo(token, contaId, TipoAtivo.RESERVA);
        assertThat(recemCriado.getPercentualCarteira()).isEqualTo(0.0);
    }

    @Test
    void patrimonio_totalPorTipoEEvolucao_6Meses_e1Mes() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(10000));
        AtivoDTO ativoA = criarAtivo(token, contaId, TipoAtivo.RESERVA);
        AtivoDTO ativoB = criarAtivo(token, contaId, TipoAtivo.RENDA_FIXA);
        aportar(token, ativoA.getId(), contaId, BigDecimal.valueOf(300));
        aportar(token, ativoB.getId(), contaId, BigDecimal.valueOf(100));

        ResponseEntity<PatrimonioDTO> resposta6 = get("/api/ativos/patrimonio?meses=6", token, PatrimonioDTO.class);
        assertThat(resposta6.getStatusCode()).isEqualTo(HttpStatus.OK);
        PatrimonioDTO patrimonio = resposta6.getBody();
        assertThat(patrimonio.getTotalPatrimonio()).isEqualByComparingTo("400");
        assertThat(patrimonio.getEvolucaoMensal()).hasSize(6);
        // cronológico: primeiro ponto é o mês mais antigo
        List<String> meses = patrimonio.getEvolucaoMensal().stream().map(PatrimonioDTO.EvolucaoMensal::getMes).toList();
        List<String> ordenado = new java.util.ArrayList<>(meses);
        ordenado.sort(String::compareTo);
        assertThat(meses).isEqualTo(ordenado);
        // último ponto (mês atual) reflete o total corrente
        assertThat(patrimonio.getEvolucaoMensal().get(5).getValor()).isEqualByComparingTo("400");

        BigDecimal totalPorTipo = patrimonio.getPorTipo().stream()
                .map(PatrimonioDTO.ResumoTipo::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPorTipo).isEqualByComparingTo("400");

        ResponseEntity<PatrimonioDTO> resposta1 = get("/api/ativos/patrimonio?meses=1", token, PatrimonioDTO.class);
        assertThat(resposta1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta1.getBody().getEvolucaoMensal()).hasSize(1);
        assertThat(resposta1.getBody().getEvolucaoMensal().get(0).getValor()).isEqualByComparingTo("400");
    }

    // ---------- helpers ----------

    private AtivoDTO criarAtivo(String token, Long contaId, TipoAtivo tipo) {
        AtivoDTO dto = new AtivoDTO();
        dto.setNome("Ativo Teste");
        dto.setTipo(tipo);
        dto.setContaId(contaId);
        dto.setCor("#112233");
        dto.setIcone("chart");
        ResponseEntity<AtivoDTO> resposta = post("/api/ativos", dto, token, AtivoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private AtivoDTO aportar(String token, Long ativoId, Long contaId, BigDecimal valor) {
        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setContaId(contaId);
        dto.setValor(valor);
        dto.setData(LocalDate.now());
        ResponseEntity<AtivoDTO> resposta = patch("/api/ativos/" + ativoId + "/aportar", dto, token, AtivoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private AtivoDTO resgatar(String token, Long ativoId, Long contaId, BigDecimal valor) {
        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setContaId(contaId);
        dto.setValor(valor);
        dto.setData(LocalDate.now());
        ResponseEntity<AtivoDTO> resposta = patch("/api/ativos/" + ativoId + "/resgatar", dto, token, AtivoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private List<MovimentacaoAtivoDTO> movimentacoes(String token, Long ativoId) {
        ResponseEntity<List<MovimentacaoAtivoDTO>> resposta = get("/api/ativos/" + ativoId + "/movimentacoes", token,
                new ParameterizedTypeReference<List<MovimentacaoAtivoDTO>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private List<AtivoDTO> listarAtivos(String token) {
        ResponseEntity<List<AtivoDTO>> resposta = get("/api/ativos", token, new ParameterizedTypeReference<List<AtivoDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private BigDecimal saldoConta(String token, Long contaId) {
        ResponseEntity<List<ContaDTO>> resposta = get("/api/contas", token, new ParameterizedTypeReference<List<ContaDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream()
                .filter(c -> c.getId().equals(contaId))
                .findFirst()
                .orElseThrow()
                .getSaldo();
    }
}
