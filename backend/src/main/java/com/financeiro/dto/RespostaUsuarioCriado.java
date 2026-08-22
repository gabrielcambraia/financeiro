package com.financeiro.dto;

import com.financeiro.entity.enums.PapelUsuario;

import java.time.LocalDateTime;

/**
 * Resposta de {@code POST /api/usuarios}. Carrega a senha temporária em
 * texto puro — só aparece aqui, uma única vez; {@code listar()} nunca a
 * expõe. O DONO precisa repassá-la ao membro fora do sistema.
 */
public record RespostaUsuarioCriado(
        Long id,
        String nome,
        String email,
        PapelUsuario papel,
        LocalDateTime criadoEm,
        String senhaTemporaria
) {
}
