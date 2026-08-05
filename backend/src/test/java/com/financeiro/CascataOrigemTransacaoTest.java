package com.financeiro;

import com.financeiro.dto.AtivoDTO;
import com.financeiro.dto.ContaDTO;
import com.financeiro.dto.DividaDTO;
import com.financeiro.dto.MetaDTO;
import com.financeiro.dto.MovimentacaoAtivoDTO;
import com.financeiro.dto.RespostaImpacto;
import com.financeiro.dto.TransacaoDTO;
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
 * Testes de integração que validam a cascata bidirecional entre entidades
 * de origem (Ativo, Meta, Dívida) e as transações que elas geram, além
 * dos endpoints de preview de impacto.
 */
class CascataOrigemTransacaoTest extends TesteIntegracaoBase {

    // ─── Ativo ───────────────────────────────────────────────────────────────

    @Test
    void cancelarAtivo_comAportes_cancelaTransacoesEReverteSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(300));

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("700");

        ResponseEntity<AtivoDTO> resp = patch("/api/ativos/" + ativo.getId() + "/cancelar", null, token, AtivoDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // saldo deve voltar ao estado original
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000");

        // valorAtual do ativo zerado
        List<AtivoDTO> ativos = listarAtivos(token);
        AtivoDTO cancelado = ativos.stream().filter(a -> a.getId().equals(ativo.getId())).findFirst().orElseThrow();
        assertThat(cancelado.getValorAtual()).isEqualByComparingTo("0");
        assertThat(cancelado.getDataCancelamento()).isNotNull();
    }

    @Test
    void cancelarAtivo_comAporteEResgate_reverteSaldoCorreto() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(300));
        resgatar(token, ativo.getId(), contaId, BigDecimal.valueOf(100));

        // saldo deve ser 800 (1000 - 300 + 100)
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("800");

        patch("/api/ativos/" + ativo.getId() + "/cancelar", null, token, AtivoDTO.class);

        // ao cancelar, o aporte não-cancelado (200 restantes como valorAtual) gera revert
        // mas a transação de resgate já havia creditado a conta — ao cancelar a tx de resgate,
        // desconta novamente. Resultado: 800 - 200 (reverso do aporte líquido) = 1000? Não,
        // a lógica cancela CADA transação individualmente:
        // - tx aporte (DESPESA, saldoAjustado=true): reverte → +300 na conta
        // - tx resgate (RECEITA, saldoAjustado=true): reverte → -100 na conta
        // saldo final: 800 + 300 - 100 = 1000
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000");
    }

    @Test
    void excluirAtivo_bloqueiaEnquantoValorAtualPositivo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(50));

        ResponseEntity<Map> resp = deleteComCorpo("/api/ativos/" + ativo.getId(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void excluirAtivo_aposResgateTotal_removeLimpo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(200));
        resgatar(token, ativo.getId(), contaId, BigDecimal.valueOf(200));

        ResponseEntity<Void> resp = delete("/api/ativos/" + ativo.getId(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<AtivoDTO> ativos = listarAtivos(token);
        assertThat(ativos).noneMatch(a -> a.getId().equals(ativo.getId()));
    }

    // ─── Meta ─────────────────────────────────────────────────────────────────

    @Test
    void cancelarMeta_comAportes_cancelaTransacoesComMetaId_revertendoSaldo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, BigDecimal.valueOf(500));

        aportarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(200));
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("800");

        ResponseEntity<MetaDTO> resp = patch("/api/metas/" + meta.getId() + "/cancelar", null, token, MetaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000");

        ResponseEntity<List<MetaDTO>> metas = get("/api/metas", token, new ParameterizedTypeReference<>() {});
        MetaDTO cancelada = metas.getBody().stream().filter(m -> m.getId().equals(meta.getId())).findFirst().orElseThrow();
        assertThat(cancelada.getValorAtual()).isEqualByComparingTo("0");
        assertThat(cancelada.getDataCancelamento()).isNotNull();
    }

    @Test
    void excluirMeta_bloqueiaEnquantoValorAtualPositivo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, BigDecimal.valueOf(500));
        aportarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(100));

        ResponseEntity<Map> resp = deleteComCorpo("/api/metas/" + meta.getId(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void excluirMeta_aposResgateTotal_desvinculaTransacoes_preservandoHistorico() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, BigDecimal.valueOf(500));
        aportarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(150));
        resgatarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(150));

        ResponseEntity<Void> resp = delete("/api/metas/" + meta.getId(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<MetaDTO>> metas = get("/api/metas", token, new ParameterizedTypeReference<>() {});
        assertThat(metas.getBody()).noneMatch(m -> m.getId().equals(meta.getId()));
    }

    // ─── Dívida (regressão do fluxo já existente) ────────────────────────────

    @Test
    void cancelarDivida_continuaCancelandoParcelas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(300), 3, LocalDate.now());

        // Pagar a primeira parcela para que saldoAjustado=true
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());
        patch("/api/transacoes/" + parcelas.get(0).getId() + "/pagar", null, token, TransacaoDTO.class);

        BigDecimal saldoAposPagamento = saldoConta(token, contaId);

        ResponseEntity<DividaDTO> resp = patch("/api/dividas/" + divida.getId() + "/cancelar", null, token, DividaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getStatus().name()).isEqualTo("CANCELADA");

        // Saldo deve ser revertido pela parcela paga
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo(saldoAposPagamento.add(parcelas.get(0).getValor()));
    }

    @Test
    void excluirDivida_semParcelaPaga_removeTodoParcelas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(300), 3, LocalDate.now());

        ResponseEntity<Void> resp = delete("/api/dividas/" + divida.getId(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ─── Transação → origem ──────────────────────────────────────────────────

    @Test
    void cancelarTransacaoDeAporteAtivo_reverteValorAtualDoAtivo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(400));

        // Encontrar a transação de aporte (DESPESA vinculada ao ativo)
        ResponseEntity<List<TransacaoDTO>> txsResp = get(
                "/api/transacoes?month=" + LocalDate.now().toString().substring(0, 7),
                token, new ParameterizedTypeReference<>() {});
        TransacaoDTO txAporte = txsResp.getBody().stream()
                .filter(t -> t.getTipo().name().equals("DESPESA"))
                .filter(t -> t.getDataCancelamento() == null)
                .findFirst().orElseThrow();

        // Cancelar a transação de aporte diretamente
        ResponseEntity<TransacaoDTO> cancelResp = patch(
                "/api/transacoes/" + txAporte.getId() + "/cancelar?scope=UNICA",
                null, token, TransacaoDTO.class);
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // valorAtual do ativo deve ter diminuído
        List<AtivoDTO> ativos = listarAtivos(token);
        AtivoDTO ativoAtualizado = ativos.stream().filter(a -> a.getId().equals(ativo.getId())).findFirst().orElseThrow();
        assertThat(ativoAtualizado.getValorAtual()).isEqualByComparingTo("0");

        // saldo da conta deve ter voltado
        assertThat(saldoConta(token, contaId)).isEqualByComparingTo("1000");
    }

    @Test
    void excluirTransacaoDeAporteMeta_decrementaValorAtualDaMeta() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, BigDecimal.valueOf(500));
        aportarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(250));

        ResponseEntity<List<MetaDTO>> metasResp = get("/api/metas", token, new ParameterizedTypeReference<>() {});
        MetaDTO metaComValor = metasResp.getBody().stream().filter(m -> m.getId().equals(meta.getId())).findFirst().orElseThrow();
        assertThat(metaComValor.getValorAtual()).isEqualByComparingTo("250");

        // Encontrar a transação de aporte da meta
        ResponseEntity<List<TransacaoDTO>> txsResp = get(
                "/api/transacoes?month=" + LocalDate.now().toString().substring(0, 7),
                token, new ParameterizedTypeReference<>() {});
        TransacaoDTO txAporte = txsResp.getBody().stream()
                .filter(t -> t.getTipo().name().equals("DESPESA"))
                .filter(t -> t.getDataCancelamento() == null)
                .findFirst().orElseThrow();

        // Excluir a transação
        ResponseEntity<Void> delResp = delete("/api/transacoes/" + txAporte.getId() + "?scope=UNICA", token);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // valorAtual da meta deve ter voltado a 0
        ResponseEntity<List<MetaDTO>> metasAtual = get("/api/metas", token, new ParameterizedTypeReference<>() {});
        MetaDTO metaAtualizada = metasAtual.getBody().stream().filter(m -> m.getId().equals(meta.getId())).findFirst().orElseThrow();
        assertThat(metaAtualizada.getValorAtual()).isEqualByComparingTo("0");
    }

    @Test
    void cancelarParcelaPagaDeDivida_decrementaParcelasPagas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(300), 3, LocalDate.now());

        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());
        TransacaoDTO parcela1 = parcelas.get(0);

        // Pagar a parcela
        patch("/api/transacoes/" + parcela1.getId() + "/pagar", null, token, TransacaoDTO.class);

        // Verificar que parcelasPagas=1
        ResponseEntity<List<DividaDTO>> dividasResp = get("/api/dividas", token, new ParameterizedTypeReference<>() {});
        DividaDTO dividaAtual = dividasResp.getBody().stream().filter(d -> d.getId().equals(divida.getId())).findFirst().orElseThrow();
        assertThat(dividaAtual.getParcelasPagas()).isEqualTo(1);

        // Cancelar a parcela diretamente
        patch("/api/transacoes/" + parcela1.getId() + "/cancelar?scope=UNICA", null, token, TransacaoDTO.class);

        // parcelasPagas deve voltar a 0 (campo derivado baseado em saldoAjustado)
        ResponseEntity<List<DividaDTO>> dividasApos = get("/api/dividas", token, new ParameterizedTypeReference<>() {});
        DividaDTO dividaApos = dividasApos.getBody().stream().filter(d -> d.getId().equals(divida.getId())).findFirst().orElseThrow();
        assertThat(dividaApos.getParcelasPagas()).isEqualTo(0);
    }

    // ─── Endpoints de impacto ─────────────────────────────────────────────────

    @Test
    void getImpactoCancelamentoAtivo_listaAportesAtivos() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(300));

        ResponseEntity<RespostaImpacto> resp = get(
                "/api/ativos/" + ativo.getId() + "/impacto-cancelamento", token, RespostaImpacto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().bloqueado()).isFalse();
        assertThat(resp.getBody().itensAfetados()).hasSize(1);
        assertThat(resp.getBody().itensAfetados().get(0).valor()).isEqualByComparingTo("300");
    }

    @Test
    void getImpactoExclusaoAtivo_indicaBloqueioSeValorAtualPositivo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(100));

        ResponseEntity<RespostaImpacto> resp = get(
                "/api/ativos/" + ativo.getId() + "/impacto-exclusao", token, RespostaImpacto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().bloqueado()).isTrue();
        assertThat(resp.getBody().motivoBloqueio()).isNotBlank();
    }

    @Test
    void getImpactoCancelamentoMeta_listaTxsVinculadas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, BigDecimal.valueOf(500));
        aportarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(200));

        ResponseEntity<RespostaImpacto> resp = get(
                "/api/metas/" + meta.getId() + "/impacto-cancelamento", token, RespostaImpacto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().bloqueado()).isFalse();
        assertThat(resp.getBody().itensAfetados()).hasSize(1);
    }

    @Test
    void getImpactoExclusaoMeta_indicaBloqueioSeValorAtualPositivo() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, BigDecimal.valueOf(500));
        aportarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(100));

        ResponseEntity<RespostaImpacto> resp = get(
                "/api/metas/" + meta.getId() + "/impacto-exclusao", token, RespostaImpacto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().bloqueado()).isTrue();
    }

    @Test
    void getImpactoCancelamentoTransacao_deAporteAtivo_retornaOrigemComEfeito() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        AtivoDTO ativo = criarAtivo(token, contaId);
        aportar(token, ativo.getId(), contaId, BigDecimal.valueOf(300));

        ResponseEntity<List<TransacaoDTO>> txsResp = get(
                "/api/transacoes?month=" + LocalDate.now().toString().substring(0, 7),
                token, new ParameterizedTypeReference<>() {});
        TransacaoDTO txAporte = txsResp.getBody().stream()
                .filter(t -> t.getTipo().name().equals("DESPESA") && t.getDataCancelamento() == null)
                .findFirst().orElseThrow();

        ResponseEntity<RespostaImpacto> resp = get(
                "/api/transacoes/" + txAporte.getId() + "/impacto-cancelamento", token, RespostaImpacto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().bloqueado()).isFalse();
        assertThat(resp.getBody().origem()).isNotNull();
        assertThat(resp.getBody().origem().tipo()).isEqualTo("ATIVO");
        assertThat(resp.getBody().origem().efeito()).isNotBlank();
    }

    @Test
    void getImpactoCancelamentoTransacao_deAporteMeta_retornaOrigemComEfeito() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        MetaDTO meta = criarMeta(token, BigDecimal.valueOf(500));
        aportarMeta(token, meta.getId(), contaId, BigDecimal.valueOf(200));

        ResponseEntity<List<TransacaoDTO>> txsResp = get(
                "/api/transacoes?month=" + LocalDate.now().toString().substring(0, 7),
                token, new ParameterizedTypeReference<>() {});
        TransacaoDTO txAporte = txsResp.getBody().stream()
                .filter(t -> t.getTipo().name().equals("DESPESA") && t.getDataCancelamento() == null)
                .findFirst().orElseThrow();

        ResponseEntity<RespostaImpacto> resp = get(
                "/api/transacoes/" + txAporte.getId() + "/impacto-cancelamento", token, RespostaImpacto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().origem()).isNotNull();
        assertThat(resp.getBody().origem().tipo()).isEqualTo("META");
    }

    @Test
    void getImpactoCancelamentoDivida_listaParcelas() {
        String token = registrar();
        Long contaId = criarConta(token, BigDecimal.valueOf(1000));
        DividaDTO divida = criarDivida(token, contaId, BigDecimal.valueOf(300), 3, LocalDate.now());

        // Pagar uma parcela para que apareça nos itens afetados
        List<TransacaoDTO> parcelas = parcelas(token, divida.getId());
        patch("/api/transacoes/" + parcelas.get(0).getId() + "/pagar", null, token, TransacaoDTO.class);

        ResponseEntity<RespostaImpacto> resp = get(
                "/api/dividas/" + divida.getId() + "/impacto-cancelamento", token, RespostaImpacto.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().bloqueado()).isFalse();
        assertThat(resp.getBody().itensAfetados()).isNotEmpty();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private AtivoDTO criarAtivo(String token, Long contaId) {
        AtivoDTO dto = new AtivoDTO();
        dto.setNome("Ativo Cascata");
        dto.setTipo(TipoAtivo.RESERVA);
        dto.setContaId(contaId);
        dto.setCor("#334455");
        dto.setIcone("chart");
        ResponseEntity<AtivoDTO> resp = post("/api/ativos", dto, token, AtivoDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private AtivoDTO aportar(String token, Long ativoId, Long contaId, BigDecimal valor) {
        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setContaId(contaId);
        dto.setValor(valor);
        dto.setData(LocalDate.now());
        ResponseEntity<AtivoDTO> resp = patch("/api/ativos/" + ativoId + "/aportar", dto, token, AtivoDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private AtivoDTO resgatar(String token, Long ativoId, Long contaId, BigDecimal valor) {
        MovimentacaoAtivoDTO dto = new MovimentacaoAtivoDTO();
        dto.setContaId(contaId);
        dto.setValor(valor);
        dto.setData(LocalDate.now());
        ResponseEntity<AtivoDTO> resp = patch("/api/ativos/" + ativoId + "/resgatar", dto, token, AtivoDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private MetaDTO criarMeta(String token, BigDecimal valorAlvo) {
        MetaDTO dto = new MetaDTO();
        dto.setNome("Meta Cascata");
        dto.setValorAlvo(valorAlvo);
        dto.setCor("#667788");
        dto.setIcone("target");
        ResponseEntity<MetaDTO> resp = post("/api/metas", dto, token, MetaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private void aportarMeta(String token, Long metaId, Long contaId, BigDecimal valor) {
        Map<String, Object> dto = Map.of("valor", valor, "contaId", contaId, "data", LocalDate.now().toString());
        ResponseEntity<MetaDTO> resp = patch("/api/metas/" + metaId + "/aportar", dto, token, MetaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void resgatarMeta(String token, Long metaId, Long contaId, BigDecimal valor) {
        Map<String, Object> dto = Map.of("valor", valor, "contaId", contaId, "data", LocalDate.now().toString());
        ResponseEntity<MetaDTO> resp = patch("/api/metas/" + metaId + "/resgatar", dto, token, MetaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private DividaDTO criarDivida(String token, Long contaId, BigDecimal valorTotal, int parcelas, LocalDate dataInicio) {
        Map<String, Object> dto = Map.of(
                "descricao", "Dívida Cascata",
                "valorTotal", valorTotal,
                "totalParcelas", parcelas,
                "contaId", contaId,
                "dataInicio", dataInicio.toString()
        );
        ResponseEntity<DividaDTO> resp = post("/api/dividas", dto, token, DividaDTO.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody();
    }

    private List<TransacaoDTO> parcelas(String token, Long dividaId) {
        ResponseEntity<List<TransacaoDTO>> resp = get(
                "/api/dividas/" + dividaId + "/parcelas", token, new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private List<AtivoDTO> listarAtivos(String token) {
        ResponseEntity<List<AtivoDTO>> resp = get("/api/ativos", token, new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private BigDecimal saldoConta(String token, Long contaId) {
        ResponseEntity<List<ContaDTO>> resp = get("/api/contas", token, new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().stream()
                .filter(c -> c.getId().equals(contaId))
                .findFirst().orElseThrow()
                .getSaldo();
    }
}
