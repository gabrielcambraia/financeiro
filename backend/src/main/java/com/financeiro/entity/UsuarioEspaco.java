package com.financeiro.entity;

import com.financeiro.entity.enums.PapelUsuario;
import jakarta.persistence.*;
import lombok.*;

/**
 * Vínculo N:N entre {@link Usuario} e {@link Espaco}, com o papel do usuário
 * naquele espaço (DONO ou MEMBRO). Entidade de junção explícita — um
 * {@code @ManyToMany} simples não comportaria a coluna {@code papel}.
 */
@Entity
@Table(name = "usuarios_espacos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioEspaco {

    @EmbeddedId
    private UsuarioEspacoId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelUsuario papel;
}
