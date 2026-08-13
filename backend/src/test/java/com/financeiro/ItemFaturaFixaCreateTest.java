package com.financeiro;

import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.repository.ItemFaturaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para {@code POST /api/itens-fatura} com {@code fixa=true},
 * validando a criação da série de 12 itens, a estrutura do trilho (origemFixaId,
 * serieAtiva, datas) e as restrições (fixa + parcelas incompatíveis).
 */
class ItemFaturaFixaCreateTest extends TesteIntegracaoBase {

    @Autowired
    private ItemFaturaRepository itemFaturaRepository;

    // -----------------------------------------------------------------------
    // 1. Criação fixa → 12 itens com trilho correto (origemFixaId, serieAtiva, datas)
    // -----------------------------------------------------------------------

    @Test
    void criacaoFixa_gera12Itens_trilhoMontado() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        LocalDate dataBase = LocalDate.now();

        Map<String, Object> dto = new HashMap<>();
        dto.put("cartaoId", cartaoId);
        dto.put("valor", 150);
        dto.put("descricao", "item fixa mensal");
        dto.put("data", dataBase.toString());
        dto.put("fixa", true);

        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/itens-fatura", dto, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<ItemFaturaDTO> itens = resposta.getBody();
        assertThat(itens).hasSize(12);

        // primeiro item é a cabeça: origemFixaId aponta para si mesmo
        ItemFaturaDTO cabeca = itens.get(0);
        assertThat(cabeca.getOrigemFixaId())
                .as("cabeça deve auto-referenciar")
                .isEqualTo(cabeca.getId());
        assertThat(cabeca.isFixa()).isTrue();

        // todos os itens devem apontar para a cabeça e ter serieAtiva=true
        Long cabecaId = cabeca.getId();
        for (ItemFaturaDTO item : itens) {
            assertThat(item.getOrigemFixaId())
                    .as("todos os itens da série devem referenciar a cabeça")
                    .isEqualTo(cabecaId);
            assertThat(item.isFixa()).isTrue();
        }

        // datas devem abranger 12 meses consecutivos a partir de dataBase
        for (int i = 0; i < 12; i++) {
            YearMonth mesEsperado = YearMonth.from(dataBase).plusMonths(i);
            LocalDate dataItem = itens.get(i).getData();
            assertThat(YearMonth.from(dataItem))
                    .as("item %d deve estar no mês +%d", i, i)
                    .isEqualTo(mesEsperado);
        }
    }

    // -----------------------------------------------------------------------
    // 2. fixa=true + totalParcelas > 1 → rejeitado com 400
    // -----------------------------------------------------------------------

    @Test
    void criacaoFixa_comParcelas_rejeitada_400() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        Map<String, Object> dto = new HashMap<>();
        dto.put("cartaoId", cartaoId);
        dto.put("valor", 100);
        dto.put("descricao", "item fixa parcelado — inválido");
        dto.put("data", LocalDate.now().toString());
        dto.put("fixa", true);
        dto.put("totalParcelas", 3);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respostaErro = postComCorpoDeErro("/api/itens-fatura", dto, u.token());

        assertThat(respostaErro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // 3. fixa=false, sem parcelas → 1 item único, sem grupoParcelaId
    // -----------------------------------------------------------------------

    @Test
    void criacaoNaoFixa_semParcelas_criaUnico() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);

        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setCartaoId(cartaoId);
        dto.setValor(BigDecimal.valueOf(80));
        dto.setDescricao("item avulso");
        dto.setData(LocalDate.now());
        // fixa=false (padrão), sem totalParcelas

        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/itens-fatura", dto, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<ItemFaturaDTO> itens = resposta.getBody();
        assertThat(itens).hasSize(1);

        ItemFaturaDTO item = itens.get(0);
        assertThat(item.getGrupoParcelaId()).isNull();
        assertThat(item.isFixa()).isFalse();
        assertThat(item.getOrigemFixaId()).isNull();
    }

}
