package com.financeiro.dto;

import com.financeiro.entity.enums.PapelUsuario;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RespostaMembro {
    private Long usuarioId;
    private String nome;
    private String email;
    private PapelUsuario papel;
    private boolean precisaTrocarSenha;
}
