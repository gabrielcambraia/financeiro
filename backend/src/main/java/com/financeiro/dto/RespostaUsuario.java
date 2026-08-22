package com.financeiro.dto;

import com.financeiro.entity.enums.PapelUsuario;

import java.time.LocalDateTime;

public record RespostaUsuario(
        Long id,
        String nome,
        String email,
        String telefone,
        PapelUsuario papel,
        LocalDateTime criadoEm
) {
}
