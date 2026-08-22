package com.financeiro;

import com.financeiro.dto.RequisicaoAtualizarUsuario;
import com.financeiro.dto.RequisicaoCriarUsuario;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaUsuario;
import com.financeiro.dto.RespostaUsuarioCriado;
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
 * Integração do {@code UsuarioController}: listagem, criação e edição
 * (nome/e-mail/telefone/papel) de usuários do espaço. Garante controle de
 * acesso (apenas DONO), isolamento entre espaços e as restrições de negócio
 * (não alterar o próprio papel, não editar usuário de outro espaço).
 */
class UsuarioTest extends TesteIntegracaoBase {

    // -----------------------------------------------------------------------
    // GET /api/usuarios — listagem
    // -----------------------------------------------------------------------

    @Test
    void dono_listaUsuariosDoProprioEspaco() {
        RespostaAutenticacao dono = registrarCompleto("Dono Lista", "dono.lista." + UUID.randomUUID() + "@teste.com");
        registrarComoMembro(dono.getEspacoId());

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
    // PUT /api/usuarios/{id} — edição (nome/e-mail/telefone/papel)
    // -----------------------------------------------------------------------

    @Test
    void dono_editaNomeEmailTelefoneEPapelDoMembro() {
        RespostaAutenticacao dono = registrarCompleto("Dono Edita", "dono.edita." + UUID.randomUUID() + "@teste.com");
        registrarComoMembro(dono.getEspacoId());

        Long membroId = listarUsuarios(dono.getToken()).stream()
                .filter(u -> u.papel() == PapelUsuario.MEMBRO)
                .findFirst().orElseThrow().id();

        RespostaUsuario atualizado = atualizar(dono.getToken(), membroId,
                "Membro Renomeado", "membro.renomeado." + UUID.randomUUID() + "@teste.com", "11988887777", PapelUsuario.DONO);

        assertThat(atualizado.nome()).isEqualTo("Membro Renomeado");
        assertThat(atualizado.telefone()).isEqualTo("11988887777");
        assertThat(atualizado.papel()).isEqualTo(PapelUsuario.DONO);

        // confirmar via listagem
        List<RespostaUsuario> usuarios = listarUsuarios(dono.getToken());
        assertThat(usuarios).extracting(RespostaUsuario::papel)
                .containsOnly(PapelUsuario.DONO);
    }

    @Test
    void dono_editaProprioNomeEmailTelefone_semAlterarPapel() {
        RespostaAutenticacao dono = registrarCompleto("Dono AutoEdita", "dono.autoedita." + UUID.randomUUID() + "@teste.com");

        RespostaUsuario atualizado = atualizar(dono.getToken(), dono.getUsuarioId(),
                "Dono Renomeado", dono.getEmail(), "11977776666", PapelUsuario.DONO);

        assertThat(atualizado.nome()).isEqualTo("Dono Renomeado");
        assertThat(atualizado.telefone()).isEqualTo("11977776666");
        assertThat(atualizado.papel()).isEqualTo(PapelUsuario.DONO);
    }

    @Test
    void dono_naoAlteraProprioPapel_400() {
        RespostaAutenticacao dono = registrarCompleto("Dono Self", "dono.self." + UUID.randomUUID() + "@teste.com");

        RequisicaoAtualizarUsuario req = new RequisicaoAtualizarUsuario();
        req.setNome(dono.getNome());
        req.setEmail(dono.getEmail());
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<Map> resposta = putComCorpoDeErro(
                "/api/usuarios/" + dono.getUsuarioId(), req, dono.getToken());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resposta.getBody().get("mensagem"))
                .contains("próprio papel");
    }

    @Test
    void editar_emailJaCadastrado_409() {
        RespostaAutenticacao dono = registrarCompleto("Dono EmailDup", "dono.emaildup." + UUID.randomUUID() + "@teste.com");
        registrarComoMembro(dono.getEspacoId());

        Long membroId = listarUsuarios(dono.getToken()).stream()
                .filter(u -> u.papel() == PapelUsuario.MEMBRO)
                .findFirst().orElseThrow().id();

        RequisicaoAtualizarUsuario req = new RequisicaoAtualizarUsuario();
        req.setNome("Membro");
        req.setEmail(dono.getEmail()); // já pertence ao dono
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<Map> resposta = putComCorpoDeErro(
                "/api/usuarios/" + membroId, req, dono.getToken());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void membro_naoEdita_403() {
        RespostaAutenticacao dono = registrarCompleto("Dono403Papel", "dono.403papel." + UUID.randomUUID() + "@teste.com");
        String tokenMembro = registrarComoMembro(dono.getEspacoId());

        RequisicaoAtualizarUsuario req = new RequisicaoAtualizarUsuario();
        req.setNome(dono.getNome());
        req.setEmail(dono.getEmail());
        req.setPapel(PapelUsuario.DONO);

        ResponseEntity<Map> resposta = putComCorpoDeErro(
                "/api/usuarios/" + dono.getUsuarioId(), req, tokenMembro);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dono_naoEditaUsuarioDeOutroEspaco_404() {
        RespostaAutenticacao donoA = registrarCompleto("Dono Espaco A", "donoA." + UUID.randomUUID() + "@teste.com");
        RespostaAutenticacao donoB = registrarCompleto("Dono Espaco B", "donoB." + UUID.randomUUID() + "@teste.com");

        RequisicaoAtualizarUsuario req = new RequisicaoAtualizarUsuario();
        req.setNome(donoB.getNome());
        req.setEmail(donoB.getEmail());
        req.setPapel(PapelUsuario.MEMBRO);

        // donoA tenta editar donoB (espaço diferente) — busca é escopada por
        // espaço (findByIdAndEspacoId), então o id de outro espaço não é
        // distinguível de um id inexistente: 404, não 403.
        ResponseEntity<Map> resposta = putComCorpoDeErro(
                "/api/usuarios/" + donoB.getUsuarioId(), req, donoA.getToken());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void dono_usuarioInexistente_404() {
        String token = registrar();

        RequisicaoAtualizarUsuario req = new RequisicaoAtualizarUsuario();
        req.setNome("Alguém");
        req.setEmail("alguem." + UUID.randomUUID() + "@teste.com");
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<Map> resposta = putComCorpoDeErro("/api/usuarios/999999999", req, token);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // POST /api/usuarios — criação de membro
    // -----------------------------------------------------------------------

    @Test
    void dono_criaMembro_201ComSenhaTemporaria() {
        RespostaAutenticacao dono = registrarCompleto("Dono Cria", "dono.cria." + UUID.randomUUID() + "@teste.com");

        RequisicaoCriarUsuario req = new RequisicaoCriarUsuario();
        req.setNome("Novo Membro");
        req.setEmail("novo.membro." + UUID.randomUUID() + "@teste.com");
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<RespostaUsuarioCriado> resposta = post("/api/usuarios", req, dono.getToken(), RespostaUsuarioCriado.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RespostaUsuarioCriado criado = resposta.getBody();
        assertThat(criado.papel()).isEqualTo(PapelUsuario.MEMBRO);
        assertThat(criado.senhaTemporaria()).isNotBlank();

        // membro criado com senha temporária consegue logar, mas fica preso
        // até trocar a senha (FiltroTrocaSenhaObrigatoria)
        assertThat(listarUsuarios(dono.getToken())).extracting(RespostaUsuario::email)
                .contains(criado.email());
    }

    @Test
    void criarMembro_emailDuplicado_409() {
        RespostaAutenticacao dono = registrarCompleto("Dono Dup", "dono.dup." + UUID.randomUUID() + "@teste.com");

        RequisicaoCriarUsuario req = new RequisicaoCriarUsuario();
        req.setNome("Duplicado");
        req.setEmail(dono.getEmail());
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<Map> resposta = post("/api/usuarios", req, dono.getToken(), new ParameterizedTypeReference<Map>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void membro_naoCriaUsuario_403() {
        RespostaAutenticacao dono = registrarCompleto("Dono403Criar", "dono.403criar." + UUID.randomUUID() + "@teste.com");
        String tokenMembro = registrarComoMembro(dono.getEspacoId());

        RequisicaoCriarUsuario req = new RequisicaoCriarUsuario();
        req.setNome("Outro");
        req.setEmail("outro." + UUID.randomUUID() + "@teste.com");
        req.setPapel(PapelUsuario.MEMBRO);

        ResponseEntity<Map> resposta = post("/api/usuarios", req, tokenMembro, new ParameterizedTypeReference<Map>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // -----------------------------------------------------------------------
    // Alteração de papel tem efeito imediato (papel revalidado no banco a
    // cada chamada, não confiando no claim — possivelmente desatualizado —
    // do JWT já emitido).
    // -----------------------------------------------------------------------

    @Test
    void alteracaoDePapel_temEfeitoImediato_mesmoComTokenAntigo() {
        RespostaAutenticacao donoA = registrarCompleto("Dono Imediato", "dono.imediato." + UUID.randomUUID() + "@teste.com");
        String tokenMembro = registrarComoMembro(donoA.getEspacoId());

        Long membroId = listarUsuarios(donoA.getToken()).stream()
                .filter(u -> u.papel() == PapelUsuario.MEMBRO)
                .findFirst().orElseThrow().id();

        // promove o membro a DONO — o token antigo dele ainda carrega o claim MEMBRO
        RespostaUsuario membro = listarUsuarios(donoA.getToken()).stream()
                .filter(u -> u.id().equals(membroId)).findFirst().orElseThrow();
        atualizar(donoA.getToken(), membroId, membro.nome(), membro.email(), membro.telefone(), PapelUsuario.DONO);

        // mesmo token antigo (claim desatualizado) já consegue uma ação de DONO,
        // porque a checagem revalida o papel no banco a cada chamada — prova
        // que a autorização não depende do claim do token, só do banco.
        ResponseEntity<List<RespostaUsuario>> resposta = get("/api/usuarios", tokenMembro,
                new ParameterizedTypeReference<List<RespostaUsuario>>() {});
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private RespostaUsuario atualizar(String token, Long usuarioId, String nome, String email, String telefone,
                                       PapelUsuario papel) {
        RequisicaoAtualizarUsuario req = new RequisicaoAtualizarUsuario();
        req.setNome(nome);
        req.setEmail(email);
        req.setTelefone(telefone);
        req.setPapel(papel);
        ResponseEntity<RespostaUsuario> resposta = put(
                "/api/usuarios/" + usuarioId, req, token, RespostaUsuario.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }
}
