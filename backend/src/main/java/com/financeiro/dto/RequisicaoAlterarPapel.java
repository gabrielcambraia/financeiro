package com.financeiro.dto;

import com.financeiro.entity.enums.PapelUsuario;
import lombok.Data;

@Data
public class RequisicaoAlterarPapel {
    private PapelUsuario papel;
}
