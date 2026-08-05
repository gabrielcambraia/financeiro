package com.financeiro.service.notificacao;

import com.financeiro.entity.enums.PropositoCodigo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnviadorEmailSmtp implements EnviadorNotificacao {

    private final JavaMailSender mailSender;

    @Value("${financeiro.email.remetente}")
    private String remetente;

    @Override
    public void enviar(String destinatario, PropositoCodigo proposito, String codigo) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(remetente);
            msg.setTo(destinatario);
            msg.setSubject(assunto(proposito));
            msg.setText(corpo(proposito, codigo));
            mailSender.send(msg);
            log.info("E-mail de verificação enviado para {} (proposito={})", destinatario, proposito);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail para {} (proposito={}): {}", destinatario, proposito, e.getMessage());
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
