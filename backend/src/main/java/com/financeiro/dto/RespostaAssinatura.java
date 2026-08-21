package com.financeiro.dto;

import com.financeiro.entity.enums.CodigoPlano;
import com.financeiro.entity.enums.StatusAssinatura;

import java.time.LocalDateTime;

public record RespostaAssinatura(
        Long id,
        CodigoPlano codigoPlano,
        String nomePlano,
        int limiteFiliais,
        long filiaisUsadas,
        StatusAssinatura status,
        LocalDateTime vigenciaInicio,
        LocalDateTime vigenciaFim
) {}
