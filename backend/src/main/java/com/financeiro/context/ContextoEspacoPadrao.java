package com.financeiro.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Implementação usada no modo desktop-local (perfil diferente de "nuvem"):
 * sempre resolve para o espaço padrão (id=1 por padrão), o que mantém o
 * app funcionando sem exigir login. No perfil "nuvem", quem resolve o
 * espaço é {@link ContextoEspacoSeguranca}.
 */
@Component
@Profile("!nuvem")
public class ContextoEspacoPadrao implements ContextoEspaco {

    @Value("${financeiro.espaco-padrao:1}")
    private Long espacoPadrao;

    @Override
    public Long espacoAtual() {
        return espacoPadrao;
    }
}
