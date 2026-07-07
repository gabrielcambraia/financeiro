package com.financeiro.dto;

import com.financeiro.entity.enums.PapelUsuario;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RespostaAutenticacao {
    private String token;
    private Long usuarioId;
    private String nome;
    private String email;
    private Long espacoId;
    private PapelUsuario papel;
    private boolean precisaTrocarSenha;
}
