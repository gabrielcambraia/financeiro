package com.financeiro.service.notificacao;

import com.financeiro.entity.enums.PropositoCodigo;

public interface EnviadorNotificacao {
    void enviar(String destinatario, PropositoCodigo proposito, String codigo);
}
