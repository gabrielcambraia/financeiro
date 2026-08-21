package com.financeiro;

import com.financeiro.dto.RequisicaoAlterarPapel;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaUsuario;
import com.financeiro.entity.enums.PapelUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração do {@code UsuarioController}: listagem de usuários do espaço e
 * alteração de papel. Garante controle de acesso (apenas DONO), isolamento
 * entre espaços e as restrições de negócio (não alterar o próprio papel,
 * não alterar usuário de outro espaço).
 */
class UsuarioTest extends TesteIntegracaoBase {

    // -----------------------------------------------------------------------
    // GET /api/usuarios — listagem
    // -----------------------------------------------------------------------

    @Test
    void dono_listaUsuariosDoProprioEspaco() {
        RespostaAutenticacao dono = registrarCompleto("Dono Lista", "dono.lista." + UUID.randomUUID() + "@teste.com");
        String tokenMembro = registrarComoMembro(dono.getEspacoId());

        List<RespostaUsuario> usuarios = listarUsuarios(dono.getToken());

        assertThat(usuarios).hasSize(2);
        assertThat(usuarios).extracting(RespostaUsuario::email)
                .contains(dono.getEmail());
        assertThat(usuarios).extracting(RespostaUsuario::papel)
                .containsExactlyInAnyOrder(PapelUsuario.DONO, PapelUsuario.MEMBRO);
    }

    @Test
    void dono_naoVeUsuariosDeOutroEspaco() {
        RespostaAutenticacao donoA = registrarCompleto("Dono A", "donoa." + UUID.randomUUID() + "@teste.com");
        registrarCompleto("Usuario B", "usuariob." + UUID.randomUUID() + "@teste.com");

        List<RespostaUsuario> usuarios = listarUsuarios(donoA.getToken());

        List<Long> ids = usuarios.stream().map(RespostaUsuario::id).toList();
        assertThat(ids).containsOnly(donoA.getUsuarioId());
    }

    @Test
    void membro_naoListaUsuarios_403() {
        RespostaAutenticacao dono = registrarCompleto("Dono Membro403", "dono.m403." + UUID.randomUUID() + "@teste.com");
        String tokenMembro = registrarComoMembro(dono.getEspacoId());

        ResponseEntity<Map> resposta = get("/api/usuarios", tokenMembro, new ParameterizedTypeReference<Map>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void semAutenticacao_naoListaUsuarios_401() {
        ResponseEntity<Map> resposta = restTemplate.getForEntity(url("/api/usuarios"), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listagem_ordenadaPapelDepoisNome() {
        RespostaAutenticacao dono = registrarCompleto("Artur Dono", "artur.dono." + UUID.randomUUID() + "@teste.com");
        registrarComoMembro(dono.getEspacoId()); // papel MEMBRO

        List<RespostaUsuario> usuarios = listarUsuarios(dono.getToken());

        // DONO vem antes de MEMBRO na ordenação por papel asc (D < M alfabeticamente)
        assertThat(usuarios.get(0).papel()).isEqualTo(PapelUsuario.DONO);
        assertThat(usuarios.get(1).papel()).isEqualTo(PapelUsuario.MEMBRO);
    }

    // -----------------------------------------------------------------------
    // PATCH /api/usuarios/{id}/papel — alteração de papel
    // -----------------------------------------------------------------------

    @Test
    void dono_alteraPapelDeMembroParaDono() {
        RespostaAutenticacao dono = registrarCompleto("Dono Promove", "dono.promove." + UUID.randomUUID() + "@teste.com");
        String tokenMembro = registrarComoMembro(dono.getEspacoId());

        Long membroId = listarUsuarios(dono.getToken()).stream()
                .filter(u -> u.papel() == PapelUsuario.MEMBRO)
                .findFirst().orElseThrow().id();

        RespostaUsuario atualizado = alterarPapel(dono.getToken(), membroId, PapelUsuario.DONO);

        assertThat(atualizado.papel()).isEqualTo(PapelUsuario.DONO);

        // confirmar via listagem
        List<RespostaUsuario> usuarios = listarUsuarios(dono.getToken());
        assertThat(usuarios).extracting(RespostaUsuario::papel)
                .containsOnly(PapelUsuario.DONO);
    }

    @Test
    void dono_alteraPapelDeDonoParaMembro() {
        RespostaAutenticacao dono = registrarCompleto("Dono Rebaixa", "dono.rebaixa." + UUID.randomUUID() + "@teste.com");
        registrarComoMembro(dono.getEspacoId());

        Long membroId = listarUsuarios(dono.getToken()).stream()
                .filter(u -> u.papel() == PapelUsuario.MEMBRO)
                .findFirst().orElseThrow().id();

        // promove membro → dono
        alterarPapel(dono.getToken(), membroId, PapelUsuario.DONO);
        // rebaixa de volta → membro
        RespostaUsuario atualizado = alterarPapel(dono.getToken(), membroId, PapelUsuario.MEMBRO);

        assertThat(atualizado.papel()).isEqualTo(PapelUsuario.MEMBRO);
    }

    @Test
    void dono_naoAlteraProprioRole_400() {
        RespostaAutenticacao dono = registrarCompleto("Dono Self", "dono.self." + UUID.randomUUID() + "@teste.com");

        RequisicaoAlterarPapel req = new RequisicaoAlterarPapel();
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<Map> resposta = patchComCorpoDeErro(
                "/api/usuarios/" + dono.getUsuarioId() + "/papel", req, dono.getToken());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resposta.getBody().get("mensagem"))
                .contains("próprio papel");
    }

    @Test
    void membro_naoAlteraPapel_403() {
        RespostaAutenticacao dono = registrarCompleto("Dono403Papel", "dono.403papel." + UUID.randomUUID() + "@teste.com");
        String tokenMembro = registrarComoMembro(dono.getEspacoId());

        RequisicaoAlterarPapel req = new RequisicaoAlterarPapel();
        req.setPapel(PapelUsuario.DONO);

        ResponseEntity<Map> resposta = patchComCorpoDeErro(
                "/api/usuarios/" + dono.getUsuarioId() + "/papel", req, tokenMembro);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dono_naoAlteraUsuarioDeOutroEspaco_403() {
        RespostaAutenticacao donoA = registrarCompleto("Dono Espaco A", "donoA." + UUID.randomUUID() + "@teste.com");
        RespostaAutenticacao donoB = registrarCompleto("Dono Espaco B", "donoB." + UUID.randomUUID() + "@teste.com");

        RequisicaoAlterarPapel req = new RequisicaoAlterarPapel();
        req.setPapel(PapelUsuario.MEMBRO);

        // donoA tenta alterar o papel de donoB (espaço diferente)
        ResponseEntity<Map> resposta = patchComCorpoDeErro(
                "/api/usuarios/" + donoB.getUsuarioId() + "/papel", req, donoA.getToken());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dono_usuarioInexistente_404() {
        String token = registrar();

        RequisicaoAlterarPapel req = new RequisicaoAlterarPapel();
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<Map> resposta = patchComCorpoDeErro("/api/usuarios/999999999/papel", req, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private List<RespostaUsuario> listarUsuarios(String token) {
        ResponseEntity<List<RespostaUsuario>> resposta = get("/api/usuarios", token,
                new ParameterizedTypeReference<List<RespostaUsuario>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private RespostaUsuario alterarPapel(String token, Long usuarioId, PapelUsuario papel) {
        RequisicaoAlterarPapel req = new RequisicaoAlterarPapel();
        req.setPapel(papel);
        ResponseEntity<RespostaUsuario> resposta = patch(
                "/api/usuarios/" + usuarioId + "/papel", req, token, RespostaUsuario.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }
}
