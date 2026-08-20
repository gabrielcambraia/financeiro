package com.financeiro;

import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoConta;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
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
 * Trava a regra de que {@code saldo} é derivado: nasce igual a
 * {@code saldoInicial} na criação e, a partir daí, só é alterado por
 * movimentações (via {@code ContaService.adjustBalance}), nunca por PUT.
 */
class ContaSaldoTest extends TesteIntegracaoBase {

    @Test
    void criar_defineSaldoIgualAoSaldoInicial() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(200));

        ContaDTO conta = buscarConta(token, contaId);
        assertThat(conta.getSaldo()).isEqualByComparingTo("200");
        assertThat(conta.getSaldoInicial()).isEqualByComparingTo("200");
    }

    @Test
    void atualizar_naoAlteraSaldoNemSaldoInicial() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(100));

        TransacaoDTO despesa = new TransacaoDTO();
        despesa.setContaId(contaId);
        despesa.setTipo(TipoTransacao.DESPESA);
        despesa.setTipoPagamento(TipoPagamento.DEBITO);
        despesa.setValor(BigDecimal.valueOf(30));
        despesa.setDescricao("teste");
        despesa.setData(LocalDate.now().minusDays(1));
        ResponseEntity<List<TransacaoDTO>> respostaTransacao = post("/api/transacoes", despesa, token,
                new ParameterizedTypeReference<List<TransacaoDTO>>() {
                });
        assertThat(respostaTransacao.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ContaDTO conta = buscarConta(token, contaId);
        assertThat(conta.getSaldo()).isEqualByComparingTo("70");

        // tentativa de sequestrar saldo e saldoInicial via PUT, junto de uma alteração legítima de nome
        ContaDTO alteracao = new ContaDTO();
        alteracao.setNome("Conta renomeada");
        alteracao.setTipo(TipoConta.CORRENTE);
        alteracao.setSaldo(BigDecimal.valueOf(999999));
        alteracao.setSaldoInicial(BigDecimal.valueOf(999999));
        alteracao.setCor("#123456");
        alteracao.setIcone("wallet");

        ResponseEntity<ContaDTO> respostaUpdate = put("/api/contas/" + contaId, alteracao, token, ContaDTO.class);
        assertThat(respostaUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaUpdate.getBody().getNome()).isEqualTo("Conta renomeada");

        ContaDTO depoisDoUpdate = buscarConta(token, contaId);
        assertThat(depoisDoUpdate.getNome()).isEqualTo("Conta renomeada");
        assertThat(depoisDoUpdate.getSaldo()).isEqualByComparingTo("70");
        assertThat(depoisDoUpdate.getSaldoInicial()).isEqualByComparingTo("100");
    }

    @Test
    void atualizar_semEnviarSaldoInicial_naoQuebra() {
        // Regressão: o frontend não envia saldoInicial no PUT (é imutável), então
        // o payload chega ao backend com esse campo nulo — não pode ser rejeitado
        // por bean validation (ver ContaDTO.saldoInicial).
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(150));

        Map<String, Object> alteracao = new java.util.HashMap<>();
        alteracao.put("nome", "Conta sem saldoInicial no payload");
        alteracao.put("tipo", "CORRENTE");
        alteracao.put("cor", "#654321");
        alteracao.put("icone", "wallet");

        ResponseEntity<ContaDTO> resposta = put("/api/contas/" + contaId, alteracao, token, ContaDTO.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().getSaldoInicial()).isEqualByComparingTo("150");
    }

    private ContaDTO buscarConta(String token, Long contaId) {
        ResponseEntity<List<ContaDTO>> resposta = get("/api/contas", token, new ParameterizedTypeReference<List<ContaDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().stream()
                .filter(c -> c.getId().equals(contaId))
                .findFirst()
                .orElseThrow();
    }
}
