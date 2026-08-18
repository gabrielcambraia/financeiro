package com.financeiro.service.notificacao;

import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.PropositoCodigo;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoteadorNotificacao {

    private final EnviadorEmailResend enviadorEmail;
    private final EnviadorSmsStub enviadorSms;

    @Async
    public void enviar(CanalNotificacao canal, String destinatario, PropositoCodigo proposito, String codigo) {
        switch (canal) {
            case EMAIL -> enviadorEmail.enviar(destinatario, proposito, codigo);
            case SMS -> enviadorSms.enviar(destinatario, proposito, codigo);
        }
    }
}
