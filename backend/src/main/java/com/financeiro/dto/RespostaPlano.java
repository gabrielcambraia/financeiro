package com.financeiro.dto;

import com.financeiro.entity.enums.CodigoPlano;

import java.time.LocalDateTime;

public record RespostaPlano(
        Long id,
        CodigoPlano codigo,
        String nome,
        int limiteEntidades,
        boolean ativo,
        LocalDateTime criadoEm
) {}
