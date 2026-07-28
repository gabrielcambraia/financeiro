package com.financeiro;

import com.financeiro.dto.RequisicaoAdicionarMembro;
import com.financeiro.dto.RequisicaoAlterarModulosEspaco;
import com.financeiro.dto.RequisicaoAlterarTipoEspaco;
import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaEspacoAdmin;
import com.financeiro.dto.RespostaPaginada;
import com.financeiro.entity.enums.ModuloEspaco;
import com.financeiro.entity.enums.NivelAcesso;
import com.financeiro.entity.enums.PapelUsuario;
import com.financeiro.entity.enums.TipoEspaco;
import com.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code ServicoEspacoAdmin}/{@code EspacoAdminController} — a tela de
 * admin que lista todos os espaços da plataforma (paginado, com dono e
 * vínculos) e permite reclassificar o tipo do espaço.
 */
class EspacoAdminTest extends TesteIntegracaoBase {

    private static final String SENHA = "senha12345";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void usuarioComum_naoListaEspacos_403() {
        String token = registrar();

        ResponseEntity<Map> resposta = restTemplateComErro("/api/admin/espacos?pagina=0", token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Apenas administradores podem realizar esta ação");
    }

    @Test
    void usuarioComum_naoAlteraTipo_403() {
        String token = registrar();
        RespostaAutenticacao autenticacao = registrarCompleto("Dona", "dona" + UUID.randomUUID() + "@teste.com");

        RequisicaoAlterarTipoEspaco requisicao = new RequisicaoAlterarTipoEspaco();
        requisicao.setTipo(TipoEspaco.FAMILIA);

        ResponseEntity<Map> resposta = patchComCorpoDeErro(
                "/api/admin/espacos/" + autenticacao.getEspacoId() + "/tipo", requisicao, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Apenas administradores podem realizar esta ação");
    }

    @Test
    void admin_listaEspacosPaginadosDeDezEmDez() {
        String tokenAdmin = registrarEPromoverAAdmin("admin1");
        for (int i = 0; i < 12; i++) {
            registrar();
        }

        RespostaPaginada<RespostaEspacoAdmin> pagina0 = listarEspacos(tokenAdmin, 0);
        assertThat(pagina0.itens()).hasSize(10);
        assertThat(pagina0.tamanho()).isEqualTo(10);
        assertThat(pagina0.pagina()).isEqualTo(0);
        assertThat(pagina0.totalItens()).isGreaterThanOrEqualTo(13); // 12 + o do próprio admin
        assertThat(pagina0.totalPaginas()).isEqualTo((int) Math.ceil(pagina0.totalItens() / 10.0));
    }

    @Test
    void admin_paginasNaoRepetemEspacos() {
        String tokenAdmin = registrarEPromoverAAdmin("admin2");
        for (int i = 0; i < 12; i++) {
            registrar();
        }

        RespostaPaginada<RespostaEspacoAdmin> pagina0 = listarEspacos(tokenAdmin, 0);
        RespostaPaginada<RespostaEspacoAdmin> pagina1 = listarEspacos(tokenAdmin, 1);

        List<Long> idsPagina0 = pagina0.itens().stream().map(RespostaEspacoAdmin::id).toList();
        List<Long> idsPagina1 = pagina1.itens().stream().map(RespostaEspacoAdmin::id).toList();

        assertThat(idsPagina0).doesNotContainAnyElementsOf(idsPagina1);
    }

    @Test
    void admin_espacoTrazDonoEVinculos() {
        String tokenAdmin = registrarEPromoverAAdmin("admin3");

        String emailDono = "dona" + UUID.randomUUID() + "@teste.com";
        RespostaAutenticacao dona = registrarCompleto("Dona Espaco", emailDono);

        String emailMembro = "membro" + UUID.randomUUID() + "@teste.com";
        RequisicaoAdicionarMembro requisicaoMembro = new RequisicaoAdicionarMembro();
        requisicaoMembro.setNome("Membro Espaco");
        requisicaoMembro.setEmail(emailMembro);
        ResponseEntity<Map> respostaMembro = postComCorpoDeErro("/api/espacos/membros", requisicaoMembro, dona.getToken());
        assertThat(respostaMembro.getStatusCode()).isEqualTo(HttpStatus.OK);

        RespostaEspacoAdmin espaco = buscarEspacoNaListagem(tokenAdmin, dona.getEspacoId());

        assertThat(espaco.emailDono()).isEqualTo(emailDono);
        assertThat(espaco.totalMembros()).isEqualTo(2);
        assertThat(espaco.vinculos().get(0).getPapel()).isEqualTo(PapelUsuario.DONO);
        assertThat(espaco.vinculos()).extracting(v -> v.getEmail())
                .containsExactlyInAnyOrder(emailDono, emailMembro);
    }

    @Test
    void admin_alteraTipoParaFamilia() {
        String tokenAdmin = registrarEPromoverAAdmin("admin4");
        RespostaAutenticacao usuario = registrarCompleto("Usuario Tipo", "tipo" + UUID.randomUUID() + "@teste.com");

        RequisicaoAlterarTipoEspaco requisicao = new RequisicaoAlterarTipoEspaco();
        requisicao.setTipo(TipoEspaco.FAMILIA);

        ResponseEntity<RespostaEspacoAdmin> resposta = patch(
                "/api/admin/espacos/" + usuario.getEspacoId() + "/tipo", requisicao, tokenAdmin, RespostaEspacoAdmin.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().tipo()).isEqualTo(TipoEspaco.FAMILIA);

        RespostaEspacoAdmin espaco = buscarEspacoNaListagem(tokenAdmin, usuario.getEspacoId());
        assertThat(espaco.tipo()).isEqualTo(TipoEspaco.FAMILIA);
    }

    @Test
    void admin_alteraTipoDeEspacoInexistente_404() {
        String tokenAdmin = registrarEPromoverAAdmin("admin5");

        RequisicaoAlterarTipoEspaco requisicao = new RequisicaoAlterarTipoEspaco();
        requisicao.setTipo(TipoEspaco.EMPRESA);

        ResponseEntity<Map> resposta = patchComCorpoDeErro("/api/admin/espacos/999999999/tipo", requisicao, tokenAdmin);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) resposta.getBody().get("mensagem")).contains("não encontrado");
    }

    @Test
    void admin_paginaAlemDoFim_retornaListaVazia() {
        String tokenAdmin = registrarEPromoverAAdmin("admin6");

        RespostaPaginada<RespostaEspacoAdmin> resposta = listarEspacos(tokenAdmin, 99999);
        assertThat(resposta.itens()).isEmpty();
    }

    @Test
    void admin_paginaNegativa_tratadaComoZero() {
        String tokenAdmin = registrarEPromoverAAdmin("admin7");

        ResponseEntity<RespostaPaginada<RespostaEspacoAdmin>> resposta = get(
                "/api/admin/espacos?pagina=-1", tokenAdmin, new ParameterizedTypeReference<RespostaPaginada<RespostaEspacoAdmin>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().pagina()).isEqualTo(0);
    }

    @Test
    void espacoNovo_naoTemModulosHabilitados() {
        String tokenAdmin = registrarEPromoverAAdmin("admin9");
        RespostaAutenticacao usuario = registrarCompleto("Usuario Sem Modulo", "semmodulo" + UUID.randomUUID() + "@teste.com");

        RespostaEspacoAdmin espaco = buscarEspacoNaListagem(tokenAdmin, usuario.getEspacoId());
        assertThat(espaco.modulosHabilitados()).isEmpty();
    }

    @Test
    void admin_habilitaEDesabilitaModuloWhatsappIa() {
        String tokenAdmin = registrarEPromoverAAdmin("admin10");
        RespostaAutenticacao usuario = registrarCompleto("Usuario Modulo", "modulo" + UUID.randomUUID() + "@teste.com");

        RequisicaoAlterarModulosEspaco habilitar = new RequisicaoAlterarModulosEspaco();
        habilitar.setModulos(Set.of(ModuloEspaco.WHATSAPP_IA));

        ResponseEntity<RespostaEspacoAdmin> respostaHabilitar = patch(
                "/api/admin/espacos/" + usuario.getEspacoId() + "/modulos", habilitar, tokenAdmin, RespostaEspacoAdmin.class);
        assertThat(respostaHabilitar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaHabilitar.getBody().modulosHabilitados()).containsExactly(ModuloEspaco.WHATSAPP_IA);

        RespostaEspacoAdmin espaco = buscarEspacoNaListagem(tokenAdmin, usuario.getEspacoId());
        assertThat(espaco.modulosHabilitados()).containsExactly(ModuloEspaco.WHATSAPP_IA);

        RequisicaoAlterarModulosEspaco desabilitar = new RequisicaoAlterarModulosEspaco();
        desabilitar.setModulos(Set.of());
        ResponseEntity<RespostaEspacoAdmin> respostaDesabilitar = patch(
                "/api/admin/espacos/" + usuario.getEspacoId() + "/modulos", desabilitar, tokenAdmin, RespostaEspacoAdmin.class);
        assertThat(respostaDesabilitar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaDesabilitar.getBody().modulosHabilitados()).isEmpty();
    }

    @Test
    void usuarioComum_naoAlteraModulos_403() {
        String token = registrar();
        RespostaAutenticacao dona = registrarCompleto("Dona Modulo", "donamodulo" + UUID.randomUUID() + "@teste.com");

        RequisicaoAlterarModulosEspaco requisicao = new RequisicaoAlterarModulosEspaco();
        requisicao.setModulos(Set.of(ModuloEspaco.WHATSAPP_IA));

        ResponseEntity<Map> resposta = patchComCorpoDeErro(
                "/api/admin/espacos/" + dona.getEspacoId() + "/modulos", requisicao, token);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Apenas administradores podem realizar esta ação");
    }

    @Test
    void admin_tipoInvalido_400() {
        String tokenAdmin = registrarEPromoverAAdmin("admin8");
        RespostaAutenticacao usuario = registrarCompleto("Usuario Invalido", "invalido" + UUID.randomUUID() + "@teste.com");

        ResponseEntity<Map> resposta = patchComCorpoDeErro(
                "/api/admin/espacos/" + usuario.getEspacoId() + "/tipo",
                Map.of("tipo", "SOCIEDADE"), tokenAdmin);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- helpers ----------

    private String registrarEPromoverAAdmin(String prefixo) {
        RespostaAutenticacao autenticacao = registrarCompleto(
                "Admin " + prefixo, prefixo + UUID.randomUUID() + "@teste.com");
        com.financeiro.entity.Usuario usuario = usuarioRepository.findById(autenticacao.getUsuarioId()).orElseThrow();
        usuario.setNivelAcesso(NivelAcesso.ADMIN);
        usuarioRepository.save(usuario);

        // o token emitido no registro carrega nivelAcesso=USUARIO; refaz o login
        // pra obter um token novo com o claim atualizado.
        RequisicaoLogin login = new RequisicaoLogin();
        login.setEmail(usuario.getEmail());
        login.setSenha(SENHA);
        ResponseEntity<RespostaAutenticacao> respostaLogin = restTemplate.postForEntity(
                url("/api/auth/login"), login, RespostaAutenticacao.class);
        assertThat(respostaLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaLogin.getBody().getNivelAcesso()).isEqualTo(NivelAcesso.ADMIN);
        return respostaLogin.getBody().getToken();
    }

    private RespostaPaginada<RespostaEspacoAdmin> listarEspacos(String token, int pagina) {
        ResponseEntity<RespostaPaginada<RespostaEspacoAdmin>> resposta = get(
                "/api/admin/espacos?pagina=" + pagina, token,
                new ParameterizedTypeReference<RespostaPaginada<RespostaEspacoAdmin>>() {
                });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    /** Varre as páginas até achar o espaço com o id informado. */
    private RespostaEspacoAdmin buscarEspacoNaListagem(String tokenAdmin, Long espacoId) {
        int pagina = 0;
        while (true) {
            RespostaPaginada<RespostaEspacoAdmin> resultado = listarEspacos(tokenAdmin, pagina);
            var encontrado = resultado.itens().stream().filter(e -> e.id().equals(espacoId)).findFirst();
            if (encontrado.isPresent()) {
                return encontrado.get();
            }
            if (pagina >= resultado.totalPaginas() - 1) {
                throw new AssertionError("Espaço " + espacoId + " não encontrado na listagem");
            }
            pagina++;
        }
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> restTemplateComErro(String caminho, String token) {
        return get(caminho, token, new ParameterizedTypeReference<Map>() {
        });
    }
}
