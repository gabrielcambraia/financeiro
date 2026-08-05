package com.financeiro.dto;

import com.financeiro.entity.enums.CodigoPlano;
import com.financeiro.entity.enums.ModuloEspaco;
import com.financeiro.entity.enums.PlanoEspaco;
import com.financeiro.entity.enums.TipoEspaco;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Uma linha da tela de admin de espaços: dados do espaço + e-mail do dono +
 * todos os vínculos (para exibir os e-mails ligados com seus papéis) +
 * módulos habilitados.
 */
public record RespostaEspacoAdmin(
        Long id,
        String nome,
        TipoEspaco tipo,
        PlanoEspaco plano,
        CodigoPlano codigoPlano,
        LocalDateTime criadoEm,
        String emailDono,
        int totalMembros,
        List<RespostaVinculoEspaco> vinculos,
        Set<ModuloEspaco> modulosHabilitados
) {
}
