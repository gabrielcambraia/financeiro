package com.financeiro;

import com.financeiro.dto.BancoDTO;
import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.entity.enums.NivelAcesso;
import com.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code BancoService}/{@code BancoController} — endpoints
 * {@code /api/bancos}, o único recurso global (sem espaço) e o único com
 * autorização por papel (ADMIN via {@code NivelAcesso}). Para obter um token
 * ADMIN: promove o usuário via {@code UsuarioRepository} e refaz o login —
 * o claim {@code nivelAcesso} do JWT emitido no registro fica desatualizado.
 */
class BancoAdminTest extends TesteIntegracaoBase {

    private static final String SENHA = "senha12345";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void usuarioComum_getLiberado_escritaProibida_403() {
        RespostaAutenticacao autenticacao = registrarCompleto("Usuária Comum", "comum" + java.util.UUID.randomUUID() + "@teste.com");
        String token = autenticacao.getToken();

        ResponseEntity<List<BancoDTO>> respostaGet = get("/api/bancos", token, new ParameterizedTypeReference<List<BancoDTO>>() {
        });
        assertThat(respostaGet.getStatusCode()).isEqualTo(HttpStatus.OK);

        BancoDTO novoBanco = bancoDto("Banco Teste", "BT");
        String mensagemEsperada = "Apenas administradores podem realizar esta ação";

        ResponseEntity<Map> respostaPost = postComCorpoDeErro("/api/bancos", novoBanco, token);
        assertThat(respostaPost.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respostaPost.getBody().get("mensagem")).isEqualTo(mensagemEsperada);

        ResponseEntity<Map> respostaPut = restTemplate.exchange(url("/api/bancos/1"), HttpMethod.PUT,
                new HttpEntity<>(novoBanco, autenticado(token)), Map.class);
        assertThat(respostaPut.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respostaPut.getBody().get("mensagem")).isEqualTo(mensagemEsperada);

        ResponseEntity<Map> respostaDelete = deleteComCorpo("/api/bancos/1", token);
        assertThat(respostaDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respostaDelete.getBody().get("mensagem")).isEqualTo(mensagemEsperada);
    }

    @Test
    void admin_conseguCriarAtualizarExcluirBanco() {
        String tokenAdmin = registrarEPromoverAAdmin("admin1");

        BancoDTO criado = criarBanco(tokenAdmin, "Banco Admin", "BA");

        BancoDTO alteracao = bancoDto("Banco Admin Editado", "BAE");
        ResponseEntity<BancoDTO> respostaUpdate = put("/api/bancos/" + criado.getId(), alteracao, tokenAdmin, BancoDTO.class);
        assertThat(respostaUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaUpdate.getBody().getNome()).isEqualTo("Banco Admin Editado");

        ResponseEntity<Void> respostaDelete = delete("/api/bancos/" + criado.getId(), tokenAdmin);
        assertThat(respostaDelete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void uploadLogo_pngAceito_textoRejeitado_400() {
        String tokenAdmin = registrarEPromoverAAdmin("admin2");
        BancoDTO banco = criarBanco(tokenAdmin, "Banco Logo", "BL");

        byte[] pngFalso = pngMinimo();
        ResponseEntity<BancoDTO> respostaPng = uploadLogo(tokenAdmin, banco.getId(), pngFalso, MediaType.IMAGE_PNG_VALUE, "logo.png");
        assertThat(respostaPng.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaPng.getBody().isTemLogo()).isTrue();

        ResponseEntity<Map> respostaTexto = uploadLogoComErro(tokenAdmin, banco.getId(), "conteudo".getBytes(), MediaType.TEXT_PLAIN_VALUE, "arquivo.txt");
        assertThat(respostaTexto.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respostaTexto.getBody().get("mensagem")).isEqualTo("Formato de imagem não suportado. Use PNG, JPEG ou WEBP.");
    }

    @Test
    void uploadLogo_acimaDe1MB_400() {
        String tokenAdmin = registrarEPromoverAAdmin("admin3");
        BancoDTO banco = criarBanco(tokenAdmin, "Banco Logo Grande", "BLG");

        byte[] arquivoGrande = new byte[1_000_001];
        ResponseEntity<Map> resposta = uploadLogoComErro(tokenAdmin, banco.getId(), arquivoGrande, MediaType.IMAGE_PNG_VALUE, "grande.png");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Imagem muito grande (máximo 1MB)");
    }

    @Test
    void getLogo_devolveBytesComContentType_temLogoReflete_bytesNuncaNoJson() {
        String tokenAdmin = registrarEPromoverAAdmin("admin4");
        BancoDTO banco = criarBanco(tokenAdmin, "Banco Logo Bytes", "BLB");

        byte[] png = pngMinimo();
        uploadLogo(tokenAdmin, banco.getId(), png, MediaType.IMAGE_PNG_VALUE, "logo.png");

        ResponseEntity<byte[]> respostaLogo = restTemplate.exchange(url("/api/bancos/" + banco.getId() + "/logo"),
                HttpMethod.GET, new HttpEntity<>(autenticado(tokenAdmin)), byte[].class);
        assertThat(respostaLogo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaLogo.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(respostaLogo.getBody()).isEqualTo(png);

        BancoDTO buscado = listarBancos(tokenAdmin).stream().filter(b -> b.getId().equals(banco.getId())).findFirst().orElseThrow();
        assertThat(buscado.isTemLogo()).isTrue();

        // o corpo JSON do banco nunca inclui os bytes crus da logo
        ResponseEntity<String> respostaJson = restTemplate.exchange(url("/api/bancos"), HttpMethod.GET,
                new HttpEntity<>(autenticado(tokenAdmin)), String.class);
        assertThat(respostaJson.getBody()).doesNotContain("logo\":[");
    }

    @Test
    void delete_bancoEmUsoPorContaOuCartao_400_globalMesmoEmOutroEspaco() {
        String tokenAdmin = registrarEPromoverAAdmin("admin5");
        BancoDTO banco = criarBanco(tokenAdmin, "Banco Em Uso", "BEU");

        // conta criada em outro espaço (usuário comum) usando o mesmo banco global
        String tokenOutroEspaco = registrar();
        com.financeiro.dto.ContaDTO contaDto = new com.financeiro.dto.ContaDTO();
        contaDto.setNome("Conta com banco");
        contaDto.setTipo(com.financeiro.entity.enums.TipoConta.CORRENTE);
        contaDto.setSaldoInicial(java.math.BigDecimal.TEN);
        contaDto.setCor("#000000");
        contaDto.setIcone("wallet");
        contaDto.setBancoId(banco.getId());
        ResponseEntity<com.financeiro.dto.ContaDTO> respostaConta = post("/api/contas", contaDto, tokenOutroEspaco, com.financeiro.dto.ContaDTO.class);
        assertThat(respostaConta.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // checagem de uso é global — mesmo o banco estando em uso por outro espaço, bloqueia a exclusão
        ResponseEntity<Map> resposta = deleteComCorpo("/api/bancos/" + banco.getId(), tokenAdmin);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Este banco está em uso por contas ou cartões e não pode ser excluído");
    }

    // ---------- helpers ----------

    private String registrarEPromoverAAdmin(String prefixo) {
        RespostaAutenticacao autenticacao = registrarCompleto(
                "Admin " + prefixo, prefixo + java.util.UUID.randomUUID() + "@teste.com");
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

    private BancoDTO bancoDto(String nome, String sigla) {
        BancoDTO dto = new BancoDTO();
        dto.setNome(nome);
        dto.setCorPrimaria("#123456");
        dto.setSigla(sigla);
        return dto;
    }

    private BancoDTO criarBanco(String tokenAdmin, String nome, String sigla) {
        ResponseEntity<BancoDTO> resposta = post("/api/bancos", bancoDto(nome, sigla), tokenAdmin, BancoDTO.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody();
    }

    private List<BancoDTO> listarBancos(String token) {
        ResponseEntity<List<BancoDTO>> resposta = get("/api/bancos", token, new ParameterizedTypeReference<List<BancoDTO>>() {
        });
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private byte[] pngMinimo() {
        // assinatura PNG (8 bytes) — suficiente pro content-type ser aceito; o
        // BancoService não valida o conteúdo do arquivo, só o content-type declarado.
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private ResponseEntity<BancoDTO> uploadLogo(String token, Long bancoId, byte[] conteudo, String contentType, String nomeArquivo) {
        MultiValueMap<String, Object> corpo = corpoMultipart(conteudo, contentType, nomeArquivo);
        HttpHeaders headers = autenticado(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(url("/api/bancos/" + bancoId + "/logo"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), BancoDTO.class);
    }

    private ResponseEntity<Map> uploadLogoComErro(String token, Long bancoId, byte[] conteudo, String contentType, String nomeArquivo) {
        MultiValueMap<String, Object> corpo = corpoMultipart(conteudo, contentType, nomeArquivo);
        HttpHeaders headers = autenticado(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(url("/api/bancos/" + bancoId + "/logo"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), Map.class);
    }

    private MultiValueMap<String, Object> corpoMultipart(byte[] conteudo, String contentType, String nomeArquivo) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource recurso = new org.springframework.core.io.ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeArquivo;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        HttpEntity<org.springframework.core.io.ByteArrayResource> parte = new HttpEntity<>(recurso, partHeaders);
        corpo.add("arquivo", parte);
        return corpo;
    }
}
