package com.financeiro;

import com.financeiro.dto.RequisicaoLogin;
import com.financeiro.dto.RespostaAutenticacao;
import com.financeiro.dto.RespostaConfiguracaoPlataforma;
import com.financeiro.entity.enums.NivelAcesso;
import com.financeiro.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@code ConfiguracaoPlataformaService}/{@code ConfiguracaoPlataformaController}
 * — dois slots de logo independentes: a logo global usada como favicon e
 * como ícone da barra lateral (/logo), e o banner da tela de login
 * (/logo-login). GET é público (sem token) nos dois; upload/remoção é
 * restrito a administradores.
 */
class ConfiguracaoPlataformaTest extends TesteIntegracaoBase {

    private static final String SENHA = "senha12345";

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void publico_obtemInfoSemAutenticacao() {
        ResponseEntity<RespostaConfiguracaoPlataforma> resposta = restTemplate.getForEntity(
                url("/api/configuracao-plataforma"), RespostaConfiguracaoPlataforma.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void usuarioComum_naoEnviaLogo_403() {
        String token = registrar();

        MultiValueMap<String, Object> corpo = corpoMultipart(pngMinimo(), MediaType.IMAGE_PNG_VALUE, "logo.png");
        HttpHeaders headers = autenticado(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> resposta = restTemplate.exchange(url("/api/configuracao-plataforma/logo"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Apenas administradores podem realizar esta ação");
    }

    @Test
    void usuarioComum_naoRemoveLogo_403() {
        String token = registrar();

        ResponseEntity<Map> resposta = restTemplate.exchange(url("/api/configuracao-plataforma/logo"), HttpMethod.DELETE,
                new HttpEntity<>(autenticado(token)), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Apenas administradores podem realizar esta ação");
    }

    @Test
    void admin_enviaObtemERemoveLogo() {
        String tokenAdmin = registrarEPromoverAAdmin("admin1");

        // sem logo: info reflete isso
        ResponseEntity<RespostaConfiguracaoPlataforma> respostaInicial = restTemplate.getForEntity(
                url("/api/configuracao-plataforma"), RespostaConfiguracaoPlataforma.class);
        assertThat(respostaInicial.getBody().temLogo()).isFalse();

        byte[] png = pngMinimo();
        MultiValueMap<String, Object> corpo = corpoMultipart(png, MediaType.IMAGE_PNG_VALUE, "logo.png");
        HttpHeaders headers = autenticado(tokenAdmin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<RespostaConfiguracaoPlataforma> respostaUpload = restTemplate.exchange(
                url("/api/configuracao-plataforma/logo"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), RespostaConfiguracaoPlataforma.class);
        assertThat(respostaUpload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaUpload.getBody().temLogo()).isTrue();

        ResponseEntity<byte[]> respostaLogo = restTemplate.getForEntity(
                url("/api/configuracao-plataforma/logo"), byte[].class);
        assertThat(respostaLogo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaLogo.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(respostaLogo.getBody()).isEqualTo(png);

        ResponseEntity<RespostaConfiguracaoPlataforma> respostaRemocao = restTemplate.exchange(
                url("/api/configuracao-plataforma/logo"), HttpMethod.DELETE,
                new HttpEntity<>(autenticado(tokenAdmin)), RespostaConfiguracaoPlataforma.class);
        assertThat(respostaRemocao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaRemocao.getBody().temLogo()).isFalse();

        ResponseEntity<byte[]> respostaLogoRemovida = restTemplate.getForEntity(
                url("/api/configuracao-plataforma/logo"), byte[].class);
        assertThat(respostaLogoRemovida.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void uploadLogo_formatoInvalido_400() {
        String tokenAdmin = registrarEPromoverAAdmin("admin2");

        MultiValueMap<String, Object> corpo = corpoMultipart("conteudo".getBytes(), MediaType.TEXT_PLAIN_VALUE, "arquivo.txt");
        HttpHeaders headers = autenticado(tokenAdmin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> resposta = restTemplate.exchange(url("/api/configuracao-plataforma/logo"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Formato de imagem não suportado. Use PNG, JPEG ou WEBP.");
    }

    @Test
    void uploadLogo_acimaDe1MB_400() {
        String tokenAdmin = registrarEPromoverAAdmin("admin3");

        byte[] arquivoGrande = new byte[1_000_001];
        MultiValueMap<String, Object> corpo = corpoMultipart(arquivoGrande, MediaType.IMAGE_PNG_VALUE, "grande.png");
        HttpHeaders headers = autenticado(tokenAdmin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> resposta = restTemplate.exchange(url("/api/configuracao-plataforma/logo"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody().get("mensagem")).isEqualTo("Imagem muito grande (máximo 1MB)");
    }

    @Test
    void usuarioComum_naoEnviaLogoLogin_403() {
        String token = registrar();

        MultiValueMap<String, Object> corpo = corpoMultipart(pngMinimo(), MediaType.IMAGE_PNG_VALUE, "logo.png");
        HttpHeaders headers = autenticado(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> resposta = restTemplate.exchange(url("/api/configuracao-plataforma/logo-login"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_enviaObtemERemoveLogoLogin_semAfetarLogoDaBarraLateral() {
        String tokenAdmin = registrarEPromoverAAdmin("admin4");

        // Envia a logo "normal" (barra lateral) primeiro, pra garantir que os
        // dois slots não se pisam.
        byte[] pngBarraLateral = pngMinimo();
        enviarLogo(tokenAdmin, pngBarraLateral, "logo.png");

        byte[] pngLogin = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01};
        MultiValueMap<String, Object> corpo = corpoMultipart(pngLogin, MediaType.IMAGE_PNG_VALUE, "login.png");
        HttpHeaders headers = autenticado(tokenAdmin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<RespostaConfiguracaoPlataforma> respostaUpload = restTemplate.exchange(
                url("/api/configuracao-plataforma/logo-login"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), RespostaConfiguracaoPlataforma.class);
        assertThat(respostaUpload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaUpload.getBody().temLogoLogin()).isTrue();
        assertThat(respostaUpload.getBody().temLogo()).isTrue();

        ResponseEntity<byte[]> respostaLogoLogin = restTemplate.getForEntity(
                url("/api/configuracao-plataforma/logo-login"), byte[].class);
        assertThat(respostaLogoLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaLogoLogin.getBody()).isEqualTo(pngLogin);

        // O slot da barra lateral continua intacto, com o conteúdo original.
        ResponseEntity<byte[]> respostaLogoBarraLateral = restTemplate.getForEntity(
                url("/api/configuracao-plataforma/logo"), byte[].class);
        assertThat(respostaLogoBarraLateral.getBody()).isEqualTo(pngBarraLateral);

        ResponseEntity<RespostaConfiguracaoPlataforma> respostaRemocao = restTemplate.exchange(
                url("/api/configuracao-plataforma/logo-login"), HttpMethod.DELETE,
                new HttpEntity<>(autenticado(tokenAdmin)), RespostaConfiguracaoPlataforma.class);
        assertThat(respostaRemocao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaRemocao.getBody().temLogoLogin()).isFalse();
        // Remover a logo do login não mexe na da barra lateral.
        assertThat(respostaRemocao.getBody().temLogo()).isTrue();

        ResponseEntity<byte[]> respostaLogoLoginRemovida = restTemplate.getForEntity(
                url("/api/configuracao-plataforma/logo-login"), byte[].class);
        assertThat(respostaLogoLoginRemovida.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private void enviarLogo(String tokenAdmin, byte[] conteudo, String nomeArquivo) {
        MultiValueMap<String, Object> corpo = corpoMultipart(conteudo, MediaType.IMAGE_PNG_VALUE, nomeArquivo);
        HttpHeaders headers = autenticado(tokenAdmin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<RespostaConfiguracaoPlataforma> resposta = restTemplate.exchange(
                url("/api/configuracao-plataforma/logo"), HttpMethod.POST,
                new HttpEntity<>(corpo, headers), RespostaConfiguracaoPlataforma.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String registrarEPromoverAAdmin(String prefixo) {
        RespostaAutenticacao autenticacao = registrarCompleto(
                "Admin " + prefixo, prefixo + UUID.randomUUID() + "@teste.com");
        com.financeiro.entity.Usuario usuario = usuarioRepository.findById(autenticacao.getUsuarioId()).orElseThrow();
        usuario.setNivelAcesso(NivelAcesso.ADMIN);
        usuarioRepository.save(usuario);

        RequisicaoLogin login = new RequisicaoLogin();
        login.setEmail(usuario.getEmail());
        login.setSenha(SENHA);
        ResponseEntity<RespostaAutenticacao> respostaLogin = restTemplate.postForEntity(
                url("/api/auth/login"), login, RespostaAutenticacao.class);
        assertThat(respostaLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaLogin.getBody().getNivelAcesso()).isEqualTo(NivelAcesso.ADMIN);
        return respostaLogin.getBody().getToken();
    }

    private byte[] pngMinimo() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private MultiValueMap<String, Object> corpoMultipart(byte[] conteudo, String contentType, String nomeArquivo) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        ByteArrayResource recurso = new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeArquivo;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        HttpEntity<ByteArrayResource> parte = new HttpEntity<>(recurso, partHeaders);
        corpo.add("arquivo", parte);
        return corpo;
    }
}
