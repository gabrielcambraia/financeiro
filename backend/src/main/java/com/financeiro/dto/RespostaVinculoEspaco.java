package com.financeiro.dto;

import com.financeiro.entity.enums.PapelUsuario;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Um vínculo usuário↔espaço, usado na tela de admin para listar todos os
 * e-mails ligados a um espaço (com o papel de cada um). Ver
 * {@link com.financeiro.repository.UsuarioEspacoRepository#listarVinculosDosEspacos}.
 */
@Data
@AllArgsConstructor
public class RespostaVinculoEspaco {
    private Long espacoId;
    private Long usuarioId;
    private String nome;
    private String email;
    private PapelUsuario papel;
}
