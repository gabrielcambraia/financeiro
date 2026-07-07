package com.financeiro.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/** Chave composta de {@link UsuarioEspaco}. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioEspacoId implements Serializable {
    private Long usuarioId;
    private Long espacoId;
}
