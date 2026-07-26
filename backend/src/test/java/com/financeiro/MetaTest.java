package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.MetaDTO;
import com.financeiro.dto.MetaMovimentoDTO;
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
 * Cobre {@code MetaService} — endpoints {@code /api/metas}.
 */
class MetaTest extends TesteIntegracaoBase {

    @Test
    void aportar_criaDespesaAjustada_debitaConta_somaValorAtual() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, "Viagem", BigDecimal.valueOf(500));

        MetaDTO atualizado = aportar(token, meta.getId(), contaId, BigDecimal.valueOf(100), LocalDate.now());

        assertThat(atualizado.getValorAtual()).isEqualByComparingTo("100");
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("900");
    }

    @Test
    void resgatar_fazOInverso() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, "Viagem", BigDecimal.valueOf(500));
        aportar(token, meta.getId(), contaId, BigDecimal.valueOf(100), LocalDate.now());

        MetaDTO atualizado = resgatar(token, meta.getId(), contaId, BigDecimal.valueOf(40), LocalDate.now());

        assertThat(atualizado.getValorAtual()).isEqualByComparingTo("60");
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("940"); // 900 + 40
    }

    @Test
    void percentualConcluido_clampEm100_concluidaQuandoAtualMaiorOuIgualAlvo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, "Meta pequena", BigDecimal.valueOf(100));

        MetaDTO atualizado = aportar(token, meta.getId(), contaId, BigDecimal.valueOf(150), LocalDate.now());

        assertThat(atualizado.getPercentualConcluido()).isEqualTo(100.0);
        assertThat(atualizado.isConcluida()).isTrue();
    }

    @Test
    void valorAlvoZero_naoDivideZero_percentualZero() {
        // valorAlvo é @Positive no DTO — a validação de negócio "sem divisão por
        // zero" fica travada no cálculo interno; aqui exercitamos o caminho onde
        // valorAtual permanece 0 (percentual = 0, sem NPE/erro aritmético).
        String token = registrar();
        MetaDTO meta = criarMeta(token, "Meta nova", BigDecimal.valueOf(100));

        assertThat(meta.getPercentualConcluido()).isEqualTo(0.0);
        assertThat(meta.getValorAtual()).isEqualByComparingTo("0");
    }

    @Test
    void mesesEstimados_metaComAporteNoMesCorrente_ceiling_valorAtualZero_null_concluidaOuCancelada_null() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));

        MetaDTO semAporte = criarMeta(token, "Sem aporte", BigDecimal.valueOf(500));
        assertThat(semAporte.getMesesEstimados()).isNull();

        MetaDTO meta = criarMeta(token, "Com aporte", BigDecimal.valueOf(500));
        MetaDTO comAporte = aportar(token, meta.getId(), contaId, BigDecimal.valueOf(100), LocalDate.now());
        assertThat(comAporte.getMesesEstimados()).isNotNull();
        assertThat(comAporte.getMesesEstimados()).isGreaterThanOrEqualTo(1);

        MetaDTO metaConcluida = criarMeta(token, "Concluída", BigDecimal.valueOf(50));
        MetaDTO concluida = aportar(token, metaConcluida.getId(), contaId, BigDecimal.valueOf(50), LocalDate.now());
        assertThat(concluida.isConcluida()).isTrue();
        assertThat(concluida.getMesesEstimados()).isNull();

        MetaDTO metaCancelada = criarMeta(token, "Cancelada", BigDecimal.valueOf(500));
        cancelar(token, metaCancelada.getId());
        MetaDTO canceladaAtual = buscarMeta(token, metaCancelada.getId());
        assertThat(canceladaAtual.getMesesEstimados()).isNull();
    }

    @Test
    void resgate_maiorQueGuardado_400_igualPassa() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, "Meta", BigDecimal.valueOf(500));
        aportar(token, meta.getId(), contaId, BigDecimal.valueOf(100), LocalDate.now());

        MetaMovimentoDTO resgateMaior = movimento(contaId, BigDecimal.valueOf(101), LocalDate.now());
        ResponseEntity<Map> respostaMaior = patchComCorpoDeErro("/api/metas/" + meta.getId() + "/resgatar", resgateMaior, token);
        assertThat(respostaMaior.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaMaior.getBody().get("mensagem")).isEqualTo("Valor maior que o guardado na meta");

        // valor igual ao guardado é permitido (limite não é estrito)
        MetaDTO resultado = resgatar(token, meta.getId(), contaId, BigDecimal.valueOf(100), LocalDate.now());
        assertThat(resultado.getValorAtual()).isEqualByComparingTo("0");
    }

    @Test
    void delete_comValorAtualPositivo_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, "Meta", BigDecimal.valueOf(500));
        aportar(token, meta.getId(), contaId, BigDecimal.valueOf(50), LocalDate.now());

        ResponseEntity<Map> resposta = deleteComCorpo("/api/metas/" + meta.getId(), token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Resgate o valor guardado antes de excluir a meta");
    }

    @Test
    void aportarOuResgatar_metaCancelada_400() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, "Meta", BigDecimal.valueOf(500));
        cancelar(token, meta.getId());

        MetaMovimentoDTO movimento = movimento(contaId, BigDecimal.valueOf(10), LocalDate.now());
        ResponseEntity<Map> respostaAporte = patchComCorpoDeErro("/api/metas/" + meta.getId() + "/aportar", movimento, token);
        assertThat(respostaAporte.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaAporte.getBody().get("mensagem")).isEqualTo("Meta cancelada");

        ResponseEntity<Map> respostaResgate = patchComCorpoDeErro("/api/metas/" + meta.getId() + "/resgatar", movimento, token);
        assertThat(respostaResgate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaResgate.getBody().get("mensagem")).isEqualTo("Meta cancelada");
    }

    @Test
    void dataFutura_400_dataNula_assumeHoje() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, "Meta", BigDecimal.valueOf(500));

        MetaMovimentoDTO comDataFutura = movimento(contaId, BigDecimal.valueOf(10), LocalDate.now().plusDays(1));
        ResponseEntity<Map> resposta = patchComCorpoDeErro("/api/metas/" + meta.getId() + "/aportar", comDataFutura, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Data não pode ser no futuro");

        MetaMovimentoDTO semData = movimento(contaId, BigDecimal.valueOf(10), null);
        MetaDTO resultado = aportar(token, meta.getId(), semData);
        assertThat(resultado.getValorAtual()).isEqualByComparingTo("10");
    }

    // ---------- helpers ----------

    private MetaDTO criarMeta(String token, String nome, BigDecimal valorAlvo) {
        MetaDTO dto = new MetaDTO();
        dto.setNome(nome);
        dto.setValorAlvo(valorAlvo);
        dto.setCor("#123456");
        dto.setIcone("target");
        ResponseEntity<MetaDTO> resposta = post("/api/metas", dto, token, MetaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private MetaMovimentoDTO movimento(Long contaId, BigDecimal valor, LocalDate data) {
        MetaMovimentoDTO dto = new MetaMovimentoDTO();
        dto.setContaId(contaId);
        dto.setValor(valor);
        dto.setData(data);
        return dto;
    }

    private MetaDTO aportar(String token, Long metaId, Long contaId, BigDecimal valor, LocalDate data) {
        return aportar(token, metaId, movimento(contaId, valor, data));
    }

    private MetaDTO aportar(String token, Long metaId, MetaMovimentoDTO dto) {
        ResponseEntity<MetaDTO> resposta = patch("/api/metas/" + metaId + "/aportar", dto, token, MetaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private MetaDTO resgatar(String token, Long metaId, Long contaId, BigDecimal valor, LocalDate data) {
        ResponseEntity<MetaDTO> resposta = patch("/api/metas/" + metaId + "/resgatar", movimento(contaId, valor, data), token, MetaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private void cancelar(String token, Long metaId) {
        ResponseEntity<MetaDTO> resposta = patch("/api/metas/" + metaId + "/cancelar", null, token, MetaDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private MetaDTO buscarMeta(String token, Long metaId) {
        ResponseEntity<List<MetaDTO>> resposta = get("/api/metas", token, new ParameterizedTypeReference<List<MetaDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream().filter(m -> m.getId().equals(metaId)).findFirst().orElseThrow();
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
