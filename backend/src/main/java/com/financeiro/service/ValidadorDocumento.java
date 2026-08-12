package com.financeiro.service;

import com.financeiro.entity.enums.TipoPessoa;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ValidadorDocumento {

    public String limparEValidar(String documento, TipoPessoa tipoPessoa) {
        if (tipoPessoa == TipoPessoa.FISICA) {
            String limpo = documento.replaceAll("\\D", "");
            validarCpf(limpo);
            return limpo;
        } else {
            String limpo = documento.replaceAll("[.\\-/]", "").toUpperCase();
            validarCnpj(limpo);
            return limpo;
        }
    }

    private void validarCpf(String numeros) {
        if (numeros.length() != 11 || numeros.chars().distinct().count() == 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CPF inválido");
        }
        int d1 = 0, d2 = 0;
        for (int i = 0; i < 9; i++) d1 += (numeros.charAt(i) - '0') * (10 - i);
        d1 = (d1 * 10 % 11) % 10;
        for (int i = 0; i < 9; i++) d2 += (numeros.charAt(i) - '0') * (11 - i);
        d2 += d1 * 2;
        d2 = (d2 * 10 % 11) % 10;
        if (d1 != (numeros.charAt(9) - '0') || d2 != (numeros.charAt(10) - '0')) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CPF inválido");
        }
    }

    private void validarCnpj(String cnpj) {
        if (cnpj.length() != 14 || cnpj.chars().distinct().count() == 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CNPJ inválido");
        }
        // Posições 1–12: [A-Z0-9]; posições 13–14: dígitos verificadores [0-9]
        for (int i = 0; i < 12; i++) {
            char c = cnpj.charAt(i);
            if (!Character.isLetterOrDigit(c) || Character.isLowerCase(c)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CNPJ inválido");
            }
        }
        for (int i = 12; i < 14; i++) {
            if (!Character.isDigit(cnpj.charAt(i))) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CNPJ inválido");
            }
        }
        // Cálculo dos DVs: char - '0' funciona para letras também (A=17, Z=42)
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int s1 = 0, s2 = 0;
        for (int i = 0; i < 12; i++) s1 += (cnpj.charAt(i) - '0') * p1[i];
        int d1 = s1 % 11 < 2 ? 0 : 11 - s1 % 11;
        for (int i = 0; i < 13; i++) s2 += (cnpj.charAt(i) - '0') * p2[i];
        int d2 = s2 % 11 < 2 ? 0 : 11 - s2 % 11;
        if (d1 != (cnpj.charAt(12) - '0') || d2 != (cnpj.charAt(13) - '0')) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CNPJ inválido");
        }
    }
}
