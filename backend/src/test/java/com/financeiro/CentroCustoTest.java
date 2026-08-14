package com.financeiro;

import com.financeiro.dto.CentroCustoDTO;
import com.financeiro.dto.ItemFaturaDTO;
import com.financeiro.dto.RequisicaoAdicionarMembro;
import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RequisicaoTrocarSenha;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaMembroAdicionado;
import com.financeiro.dto.TransacaoDTO;
import com.financeiro.entity.enums.TipoPagamento;
import com.financeiro.entity.enums.TipoPessoa;
import com.financeiro.entity.enums.TipoTransacao;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração completa do fluxo de Centros de Custo:
 * - CRUD via API (restrição DONO/MEMBRO)
 * - Filtro por entidade (CC global vs. CC escopado)
 * - Associação com Transacao e ItemFatura
 * - Filtro de lançamentos por centroCustoId
 * - SET NULL ao excluir CC (lançamentos não apagados)
 * - Preservação do CC nas conversões débito↔crédito
 */
class CentroCustoTest extends TesteIntegracaoBase {

    // -----------------------------------------------------------------------
    // 1. CRUD básico — DONO
    // -----------------------------------------------------------------------

    @Test
    void dono_criaAtualizaExclui_cenarioPrincipal() {
        String token = registrar();

        // criar
        CentroCustoDTO criado = criarCc(token, "Consultório", "#3b82f6", null);
        assertThat(criado.getId()).isNotNull();
        assertThat(criado.getNome()).isEqualTo("Consultório");
        assertThat(criado.getCor()).isEqualTo("#3b82f6");

        // atualizar
        CentroCustoDTO payload = new CentroCustoDTO();
        payload.setNome("Consultório Médico");
        payload.setCor("#ef4444");
        ResponseEntity<CentroCustoDTO> atualizado = put(
                "/api/centros-custo/" + criado.getId(), payload, token, CentroCustoDTO.class);
        assertThat(atualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atualizado.getBody().getNome()).isEqualTo("Consultório Médico");

        // listar — aparece
        assertThat(listarCcs(token)).extracting(CentroCustoDTO::getId).contains(criado.getId());

        // excluir
        ResponseEntity<Void> excluido = delete("/api/centros-custo/" + criado.getId(), token);
        assertThat(excluido.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // listar — sumiu
        assertThat(listarCcs(token)).extracting(CentroCustoDTO::getId).doesNotContain(criado.getId());
    }

    @Test
    void nomeDuplicado_noMesmoEspaco_retorna409() {
        String token = registrar();
        criarCc(token, "Casa", "#22c55e", null);

        CentroCustoDTO duplicado = new CentroCustoDTO();
        duplicado.setNome("Casa");
        duplicado.setCor("#10b981");

        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/centros-custo", duplicado, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void nomeDuplicado_emEspacosDiferentes_permitido() {
        String tokenA = registrar();
        String tokenB = registrar();

        // ambos criam "Casa" — espaços separados, sem conflito
        assertThat(criarCc(tokenA, "Casa", "#22c55e", null).getId()).isNotNull();
        assertThat(criarCc(tokenB, "Casa", "#22c55e", null).getId()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // 2. Restrição DONO — MEMBRO deve receber 403
    // -----------------------------------------------------------------------

    @Test
    void membro_naoCriaCc_403() {
        RespostaAutenticacao dono = registrarCompleto("Dono", "dono+" + UUID.randomUUID() + "@teste.com");
        String tokenMembro = adicionarMembroEObterToken(dono.getToken());

        CentroCustoDTO payload = new CentroCustoDTO();
        payload.setNome("Proibido");
        payload.setCor("#000000");

        ResponseEntity<Map> resposta = postComCorpoDeErro("/api/centros-custo", payload, tokenMembro);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void membro_naoAtualizaCc_403() {
        RespostaAutenticacao dono = registrarCompleto("Dono", "dono+" + UUID.randomUUID() + "@teste.com");
        CentroCustoDTO cc = criarCc(dono.getToken(), "Original", "#6366f1", null);
        String tokenMembro = adicionarMembroEObterToken(dono.getToken());

        CentroCustoDTO payload = new CentroCustoDTO();
        payload.setNome("Alterado");
        payload.setCor("#000000");

        ResponseEntity<Map> resposta = restTemplate.exchange(
                url("/api/centros-custo/" + cc.getId()),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(payload, autenticado(tokenMembro)),
                Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void membro_naoExcluiCc_403() {
        RespostaAutenticacao dono = registrarCompleto("Dono", "dono+" + UUID.randomUUID() + "@teste.com");
        CentroCustoDTO cc = criarCc(dono.getToken(), "Persistente", "#8b5cf6", null);
        String tokenMembro = adicionarMembroEObterToken(dono.getToken());

        ResponseEntity<Void> resposta = delete("/api/centros-custo/" + cc.getId(), tokenMembro);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // CC deve continuar existindo
        assertThat(listarCcs(dono.getToken())).extracting(CentroCustoDTO::getId).contains(cc.getId());
    }

    @Test
    void membro_listaCC_200() {
        RespostaAutenticacao dono = registrarCompleto("Dono", "dono+" + UUID.randomUUID() + "@teste.com");
        criarCc(dono.getToken(), "Visível", "#06b6d4", null);
        String tokenMembro = adicionarMembroEObterToken(dono.getToken());

        // listagem é aberta a todos os membros
        ResponseEntity<List<CentroCustoDTO>> resposta = get(
                "/api/centros-custo", tokenMembro, new ParameterizedTypeReference<List<CentroCustoDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // 3. Filtro por entidade
    // -----------------------------------------------------------------------

    @Test
    void ccGlobal_apareceEmTodosOsFiltrosDeEntidade() {
        RespostaAutenticacao auth = registrarCompleto("Filtro", "filtro+" + UUID.randomUUID() + "@teste.com");

        // O registro já cria a 1ª entidade (PF) — buscá-la evita conflito de CPF.
        Long entidadeId = primeiraEntidadeId(auth.getToken());

        CentroCustoDTO ccGlobal = criarCc(auth.getToken(), "Global CC", "#eab308", null);

        // sem header de entidade
        assertThat(listarCcs(auth.getToken()))
                .extracting(CentroCustoDTO::getId).contains(ccGlobal.getId());

        // com header de entidade
        assertThat(listarCcsComEntidade(auth.getToken(), entidadeId))
                .extracting(CentroCustoDTO::getId).contains(ccGlobal.getId());
    }

    @Test
    void ccEscopado_naoaparece_emOutraEntidade() {
        RespostaAutenticacao auth = registrarCompleto("Escopo", "escopo+" + UUID.randomUUID() + "@teste.com");
        ativarPlanoEmpresa(auth.getEspacoId());

        // Registro já cria PF; só criamos PJ (CNPJ diferente, sem conflito).
        Long entidadePfId = primeiraEntidadeId(auth.getToken());
        Long entidadePjId = criarEntidade(auth.getToken(), "PJ", TipoPessoa.JURIDICA);

        CentroCustoDTO ccPf = criarCc(auth.getToken(), "Consultório", "#3b82f6", entidadePfId);

        // filtro pela entidade dona do CC: aparece
        assertThat(listarCcsComEntidade(auth.getToken(), entidadePfId))
                .extracting(CentroCustoDTO::getId).contains(ccPf.getId());

        // filtro por outra entidade: não aparece
        assertThat(listarCcsComEntidade(auth.getToken(), entidadePjId))
                .extracting(CentroCustoDTO::getId).doesNotContain(ccPf.getId());
    }

    // -----------------------------------------------------------------------
    // 4. Associação com lançamentos
    // -----------------------------------------------------------------------

    @Test
    void transacao_comCentroCusto_persisteCampo() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        CentroCustoDTO cc = criarCc(u.token(), "Clínica", "#ec4899", null);

        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(300));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setQuitarNaCriacao(true);
        dto.setCentroCustoId(cc.getId());

        ResponseEntity<List<TransacaoDTO>> resposta = post(
                "/api/transacoes", dto, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().get(0).getCentroCustoId()).isEqualTo(cc.getId());

        // buscar a transação individualmente confirma persistência
        Long txId = resposta.getBody().get(0).getId();
        ResponseEntity<List<Map>> todas = get("/api/transacoes?month=" + LocalDate.now().toString().substring(0, 7),
                u.token(), new ParameterizedTypeReference<List<Map>>() {});
        Map tx = todas.getBody().stream()
                .filter(t -> ((Number) t.get("id")).longValue() == txId)
                .findFirst().orElseThrow();
        assertThat(((Number) tx.get("centroCustoId")).longValue()).isEqualTo(cc.getId());
    }

    @Test
    void itemFatura_comCentroCusto_persisteCampo() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);
        CentroCustoDTO cc = criarCc(u.token(), "Equipamentos", "#f97316", null);

        ItemFaturaDTO dto = new ItemFaturaDTO();
        dto.setCartaoId(cartaoId);
        dto.setValor(BigDecimal.valueOf(500));
        dto.setDescricao("Microscópio");
        dto.setData(LocalDate.now());
        dto.setCentroCustoId(cc.getId());

        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/itens-fatura", dto, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().get(0).getCentroCustoId()).isEqualTo(cc.getId());
    }

    @Test
    void filtrarTransacoes_porCentroCusto_retornaApenasMarcadas() {
        Usuario u = registrarComConta(BigDecimal.valueOf(5000));
        CentroCustoDTO cc = criarCc(u.token(), "Clínica", "#8b5cf6", null);

        // transação COM CC
        TransacaoDTO dtoComCc = new TransacaoDTO();
        dtoComCc.setContaId(u.contaId());
        dtoComCc.setTipo(TipoTransacao.DESPESA);
        dtoComCc.setTipoPagamento(TipoPagamento.DEBITO);
        dtoComCc.setValor(BigDecimal.valueOf(100));
        dtoComCc.setData(LocalDate.now());
        dtoComCc.setDataVencimento(LocalDate.now());
        dtoComCc.setQuitarNaCriacao(true);
        dtoComCc.setCentroCustoId(cc.getId());
        Long txComCcId = post("/api/transacoes", dtoComCc, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {}).getBody().get(0).getId();

        // transação SEM CC
        Long txSemCcId = criarTransacao(u.token(), u.contaId(), BigDecimal.valueOf(200), LocalDate.now());

        String mes = LocalDate.now().toString().substring(0, 7);
        ResponseEntity<List<Map>> comFiltro = get(
                "/api/transacoes?month=" + mes + "&centroCustoId=" + cc.getId(),
                u.token(), new ParameterizedTypeReference<List<Map>>() {});
        assertThat(comFiltro.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Long> ids = comFiltro.getBody().stream()
                .map(t -> ((Number) t.get("id")).longValue())
                .toList();
        assertThat(ids).contains(txComCcId);
        assertThat(ids).doesNotContain(txSemCcId);
    }

    @Test
    void excluirCc_desvinculaLancamentos_naoApagaTransacao() {
        Usuario u = registrarComConta(BigDecimal.valueOf(1000));
        CentroCustoDTO cc = criarCc(u.token(), "Temporário", "#6b7280", null);

        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(50));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setQuitarNaCriacao(true);
        dto.setCentroCustoId(cc.getId());
        Long txId = post("/api/transacoes", dto, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {}).getBody().get(0).getId();

        // excluir CC
        assertThat(delete("/api/centros-custo/" + cc.getId(), u.token()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // transação ainda existe
        String mes = LocalDate.now().toString().substring(0, 7);
        ResponseEntity<List<Map>> todas = get("/api/transacoes?month=" + mes,
                u.token(), new ParameterizedTypeReference<List<Map>>() {});
        boolean txExiste = todas.getBody().stream()
                .anyMatch(t -> ((Number) t.get("id")).longValue() == txId);
        assertThat(txExiste).isTrue();

        // centroCustoId virou null (SET NULL)
        Map tx = todas.getBody().stream()
                .filter(t -> ((Number) t.get("id")).longValue() == txId)
                .findFirst().orElseThrow();
        assertThat(tx.get("centroCustoId")).isNull();
    }

    // -----------------------------------------------------------------------
    // 5. Preservação do CC nas conversões
    // -----------------------------------------------------------------------

    @Test
    void converterDebitoParaCredito_preservaCentroCusto() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);
        CentroCustoDTO cc = criarCc(u.token(), "Conversão", "#10b981", null);

        // cria transação débito com CC
        TransacaoDTO dto = new TransacaoDTO();
        dto.setContaId(u.contaId());
        dto.setTipo(TipoTransacao.DESPESA);
        dto.setTipoPagamento(TipoPagamento.DEBITO);
        dto.setValor(BigDecimal.valueOf(150));
        dto.setData(LocalDate.now());
        dto.setDataVencimento(LocalDate.now());
        dto.setQuitarNaCriacao(true);
        dto.setCentroCustoId(cc.getId());
        Long txId = post("/api/transacoes", dto, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {}).getBody().get(0).getId();

        // converter para crédito
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("cartaoId", cartaoId);
        ResponseEntity<List<ItemFaturaDTO>> resposta = post(
                "/api/transacoes/" + txId + "/converter-para-cartao", corpo, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().get(0).getCentroCustoId()).isEqualTo(cc.getId());
    }

    @Test
    void converterCreditoParaDebito_preservaCentroCusto() {
        Usuario u = registrarComConta(BigDecimal.valueOf(2000));
        Long cartaoId = criarCartao(u.token(), u.contaId(), 28, 5);
        CentroCustoDTO cc = criarCc(u.token(), "Conversão Crédito", "#06b6d4", null);

        // cria item fatura com CC
        ItemFaturaDTO itemDto = new ItemFaturaDTO();
        itemDto.setCartaoId(cartaoId);
        itemDto.setValor(BigDecimal.valueOf(200));
        itemDto.setDescricao("compra crédito");
        itemDto.setData(LocalDate.now());
        itemDto.setCentroCustoId(cc.getId());
        Long itemId = post("/api/itens-fatura", itemDto, u.token(),
                new ParameterizedTypeReference<List<ItemFaturaDTO>>() {}).getBody().get(0).getId();

        // converter para débito
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("contaId", u.contaId());
        ResponseEntity<List<TransacaoDTO>> resposta = post(
                "/api/itens-fatura/" + itemId + "/converter-para-debito", corpo, u.token(),
                new ParameterizedTypeReference<List<TransacaoDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().get(0).getCentroCustoId()).isEqualTo(cc.getId());
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private CentroCustoDTO criarCc(String token, String nome, String cor, Long entidadeId) {
        CentroCustoDTO dto = new CentroCustoDTO();
        dto.setNome(nome);
        dto.setCor(cor);
        dto.setEntidadeId(entidadeId);
        ResponseEntity<CentroCustoDTO> resposta = post("/api/centros-custo", dto, token, CentroCustoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private List<CentroCustoDTO> listarCcs(String token) {
        ResponseEntity<List<CentroCustoDTO>> resposta = get(
                "/api/centros-custo", token, new ParameterizedTypeReference<List<CentroCustoDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private List<CentroCustoDTO> listarCcsComEntidade(String token, Long entidadeId) {
        ResponseEntity<List<CentroCustoDTO>> resposta = get(
                "/api/centros-custo", token, entidadeId, new ParameterizedTypeReference<List<CentroCustoDTO>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    /** Retorna o ID da primeira entidade do espaço (criada automaticamente no registro). */
    @SuppressWarnings("rawtypes")
    private Long primeiraEntidadeId(String token) {
        ResponseEntity<List<Map>> resp = get("/api/entidades", token, new ParameterizedTypeReference<List<Map>>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) resp.getBody().get(0).get("id")).longValue();
    }

    /**
     * Adiciona um membro ao espaço do DONO e devolve o token do membro já com
     * senha definitiva (sem precisaTrocarSenha bloqueando as requisições).
     */
    private String adicionarMembroEObterToken(String tokenDono) {
        String email = "membro+" + UUID.randomUUID() + "@teste.com";

        RequisicaoAdicionarMembro req = new RequisicaoAdicionarMembro();
        req.setNome("Membro Teste");
        req.setEmail(email);
        ResponseEntity<RespostaMembroAdicionado> adicionado = post(
                "/api/espacos/membros", req, tokenDono, RespostaMembroAdicionado.class);
        assertThat(adicionado.getStatusCode()).isEqualTo(HttpStatus.OK);
        String senhaTemporaria = adicionado.getBody().getSenhaTemporaria();

        // login com senha temporária
        RequisicaoLogin login = new RequisicaoLogin();
        login.setEmail(email);
        login.setSenha(senhaTemporaria);
        ResponseEntity<RespostaAutenticacao> loginResp = restTemplate.postForEntity(
                url("/api/auth/login"), login, RespostaAutenticacao.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenTemp = loginResp.getBody().getToken();

        // trocar senha para liberar o filtro FiltroTrocaSenhaObrigatoria
        RequisicaoTrocarSenha trocar = new RequisicaoTrocarSenha();
        trocar.setSenhaAtual(senhaTemporaria);
        trocar.setNovaSenha("senha12345");
        ResponseEntity<RespostaAutenticacao> trocarResp = post(
                "/api/auth/trocar-senha", trocar, tokenTemp, RespostaAutenticacao.class);
        assertThat(trocarResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return trocarResp.getBody().getToken();
    }
}
