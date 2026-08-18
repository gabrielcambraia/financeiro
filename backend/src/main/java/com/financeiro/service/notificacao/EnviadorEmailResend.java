package com.financeiro.service.notificacao;

import com.financeiro.entity.enums.PropositoCodigo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Envia e-mails via API HTTPS da Resend (porta 443) em vez de SMTP direto
 * (porta 587): provedores cloud como o Render bloqueiam saída SMTP, causando
 * timeout de conexão em produção mesmo com credenciais corretas.
 */
@Slf4j
@Component
public class EnviadorEmailResend implements EnviadorNotificacao {

    private final RestClient restClient;
    private final String apiKey;
    private final String remetente;

    public EnviadorEmailResend(RestClient.Builder restClientBuilder,
                                @Value("${financeiro.email.resend.api-key}") String apiKey,
                                @Value("${financeiro.email.remetente}") String remetente) {
        this.apiKey = apiKey;
        this.remetente = remetente;

        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(10_000);

        this.restClient = restClientBuilder
                .baseUrl("https://api.resend.com")
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public void enviar(String destinatario, PropositoCodigo proposito, String codigo) {
        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", remetente,
                            "to", destinatario,
                            "subject", assunto(proposito),
                            "text", corpo(proposito, codigo)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("E-mail de verificação enviado para {} (proposito={})", destinatario, proposito);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail para {} (proposito={})", destinatario, proposito, e);
        }
    }

    private String assunto(PropositoCodigo proposito) {
        return switch (proposito) {
            case LOGIN -> "Seu código de acesso";
            case VERIFICAR_EMAIL -> "Confirme seu e-mail";
            case VERIFICAR_TELEFONE -> "Confirme seu contato";
        };
    }

    private String corpo(PropositoCodigo proposito, String codigo) {
        String acao = switch (proposito) {
            case LOGIN -> "para acessar sua conta";
            case VERIFICAR_EMAIL -> "para verificar seu e-mail";
            case VERIFICAR_TELEFONE -> "para verificar seu contato";
        };
        return String.format(
                "Seu código %s é: %s%n%nEle é válido por 10 minutos. Não compartilhe com ninguém.%n%nSe você não solicitou este código, ignore este e-mail.",
                acao, codigo);
    }
}
