package com.financeiro.seguranca;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera senhas temporárias para membros recém-criados. Charset sem
 * caracteres ambíguos (sem 0/O, 1/l/I) para facilitar a digitação quando o
 * DONO repassa a senha ao membro fora do sistema.
 */
@Component
public class GeradorSenhaTemporaria {

    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TAMANHO = 12;
    private final SecureRandom random = new SecureRandom();

    public String gerar() {
        StringBuilder sb = new StringBuilder(TAMANHO);
        for (int i = 0; i < TAMANHO; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }
        return sb.toString();
    }
}
