package com.financeiro;

import com.financeiro.dto.RecorrenciaDTO;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoTransacao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava que RecorrenciaService rejeita vínculos (conta/cartão/categoria/centro
 * de custo) que pertencem a outro espaço — o id vem do cliente e, sem essa
 * checagem, um usuário poderia referenciar recursos de outro tenant.
 */
class RecorrenciaCrossTenantTest extends TesteIntegracaoBase {

    @Test
    void criar_comContaDeOutroEspaco_rejeitada400() {
        String tokenA = registrar();
        Usuario b = registrarComConta(BigDecimal.valueOf(100));

        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setContaId(b.contaId()); // conta pertence ao espaço de B
        dto.setValor(BigDecimal.valueOf(50));
        dto.setDiaCompetencia(5);
        dto.setDataInicio(LocalDate.now().withDayOfMonth(1));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/recorrencias", dto, tokenA);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void atualizar_comContaDeOutroEspaco_rejeitada400() {
        Usuario a = registrarComConta(BigDecimal.valueOf(100));
        Usuario b = registrarComConta(BigDecimal.valueOf(200));

        RecorrenciaDTO dto = new RecorrenciaDTO();
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setContaId(a.contaId());
        dto.setValor(BigDecimal.valueOf(50));
        dto.setDiaCompetencia(5);
        dto.setDataInicio(LocalDate.now().plusMonths(1).withDayOfMonth(1));

        ResponseEntity<RecorrenciaDTO> criada = post("/api/recorrencias", dto, a.token(), RecorrenciaDTO.class);
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = criada.getBody().getId();

        dto.setContaId(b.contaId()); // tenta trocar para conta de outro espaço
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaPut = restTemplate.exchange(
                url("/api/recorrencias/" + id), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(dto, autenticado(a.token())), Map.class);

        assertThat(respostaPut.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
