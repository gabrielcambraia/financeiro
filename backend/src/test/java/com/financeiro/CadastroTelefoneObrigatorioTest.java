package com.financeiro;

import com.financeiro.dto.RequisicaoCadastrarTelefone;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate de "cadastrar telefone" ({@code FiltroCadastroTelefoneObrigatorio}):
 * telefone é obrigatório — inclusive retroativamente para contas antigas
 * sem telefone —, e a checagem tem que ceder lugar à troca de senha
 * obrigatória quando as duas se aplicam ao mesmo usuário (a senha vem
 * primeiro).
 */
class CadastroTelefoneObrigatorioTest extends TesteIntegracaoBase {

    @Test
    void usuarioSemTelefone_recebe403TelefonePendente_forDaWhitelist() {
        RespostaAutenticacao dono = registrarCompleto("Dono SemTelefone", "dono.semtel." + UUID.randomUUID() + "@teste.com");
        removerTelefone(dono.getUsuarioId());
        RespostaAutenticacao logado = login(dono.getEmail(), "senha12345");

        assertThat(logado.isPrecisaCadastrarTelefone()).isTrue();

        ResponseEntity<Map> resposta = get("/api/usuarios", logado.getToken(), new ParameterizedTypeReference<Map>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().get("codigo")).isEqualTo("TELEFONE_PENDENTE");
    }

    @Test
    void cadastrarTelefone_liberaAcessoEReemiteToken() {
        RespostaAutenticacao dono = registrarCompleto("Dono Cadastra", "dono.cadastra." + UUID.randomUUID() + "@teste.com");
        removerTelefone(dono.getUsuarioId());
        RespostaAutenticacao logado = login(dono.getEmail(), "senha12345");

        RequisicaoCadastrarTelefone req = new RequisicaoCadastrarTelefone();
        req.setTelefone("11988887777");
        ResponseEntity<RespostaAutenticacao> resposta = post("/api/auth/telefone", req, logado.getToken(), RespostaAutenticacao.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().isPrecisaCadastrarTelefone()).isFalse();

        // o novo token já libera o acesso normal
        ResponseEntity<List<RespostaUsuario>> apos = get("/api/usuarios", resposta.getBody().getToken(),
                new ParameterizedTypeReference<List<RespostaUsuario>>() {});
        assertThat(apos.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void senhaTemporariaTemPrioridadeSobreTelefonePendente() {
        RespostaAutenticacao dono = registrarCompleto("Dono Prioridade", "dono.prioridade." + UUID.randomUUID() + "@teste.com");
        // registrarComoMembro só ajusta espaço/papel; o membro nasce com
        // telefone preenchido (herdado do registro) e precisaTrocarSenha
        // continua false — então simulamos um membro criado via
        // ServicoUsuario.criar(): senha temporária + sem telefone.

        var req = new com.financeiro.dto.RequisicaoCriarUsuario();
        req.setNome("Membro Novo");
        req.setEmail("membro.novo." + UUID.randomUUID() + "@teste.com");
        req.setPapel(com.financeiro.entity.enums.PapelUsuario.MEMBRO);
        ResponseEntity<com.financeiro.dto.RespostaUsuarioCriado> criado = post(
                "/api/usuarios", req, dono.getToken(), com.financeiro.dto.RespostaUsuarioCriado.class);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        RespostaAutenticacao logadoMembro = login(criado.getBody().email(), criado.getBody().senhaTemporaria());
        assertThat(logadoMembro.isPrecisaTrocarSenha()).isTrue();
        assertThat(logadoMembro.isPrecisaCadastrarTelefone()).isTrue();

        ResponseEntity<Map> resposta = get("/api/usuarios", logadoMembro.getToken(), new ParameterizedTypeReference<Map>() {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().get("codigo")).isEqualTo("SENHA_TEMPORARIA");
    }

    /**
     * Regressão: um usuário com os dois flags ativos (senha temporária E
     * telefone pendente) precisa conseguir trocar a senha primeiro — se
     * {@code FiltroCadastroTelefoneObrigatorio} não tiver
     * {@code /api/auth/trocar-senha} na própria whitelist, ele bloqueia essa
     * chamada (o token ainda carrega {@code precisaCadastrarTelefone=true}
     * até a troca de senha reemitir um novo) e a conta fica em deadlock: não
     * consegue trocar a senha nem chegar em /cadastrar-telefone.
     */
    @Test
    void usuarioComSenhaESemTelefonePendentes_consegueTrocarSenha() {
        RespostaAutenticacao dono = registrarCompleto("Dono Deadlock", "dono.deadlock." + UUID.randomUUID() + "@teste.com");

        var req = new com.financeiro.dto.RequisicaoCriarUsuario();
        req.setNome("Membro Deadlock");
        req.setEmail("membro.deadlock." + UUID.randomUUID() + "@teste.com");
        req.setPapel(com.financeiro.entity.enums.PapelUsuario.MEMBRO);
        ResponseEntity<com.financeiro.dto.RespostaUsuarioCriado> criado = post(
                "/api/usuarios", req, dono.getToken(), com.financeiro.dto.RespostaUsuarioCriado.class);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        RespostaAutenticacao logadoMembro = login(criado.getBody().email(), criado.getBody().senhaTemporaria());
        assertThat(logadoMembro.isPrecisaTrocarSenha()).isTrue();
        assertThat(logadoMembro.isPrecisaCadastrarTelefone()).isTrue();

        var trocarSenha = new com.financeiro.dto.RequisicaoTrocarSenha();
        trocarSenha.setSenhaAtual(criado.getBody().senhaTemporaria());
        trocarSenha.setNovaSenha("novaSenha12345");
        ResponseEntity<RespostaAutenticacao> resposta = post(
                "/api/auth/trocar-senha", trocarSenha, logadoMembro.getToken(), RespostaAutenticacao.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().isPrecisaTrocarSenha()).isFalse();
        assertThat(resposta.getBody().isPrecisaCadastrarTelefone()).isTrue();
    }
}
