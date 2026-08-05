package com.financeiro.service.notificacao;

import com.financeiro.entity.enums.PropositoCodigo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// TODO: substituir por provedor real (Twilio, AWS SNS, Zenvia, etc.)
@Slf4j
@Component
public class EnviadorSmsStub implements EnviadorNotificacao {

    @Override
    public void enviar(String destinatario, PropositoCodigo proposito, String codigo) {
        log.warn("SMS não configurado — provedor pendente. Destinatário: {} | Propósito: {} | Código: [OMITIDO]",
                destinatario, proposito);
    }
}
