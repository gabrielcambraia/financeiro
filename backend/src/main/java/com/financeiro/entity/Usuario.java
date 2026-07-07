package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDateTime;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Uma pessoa que acessa o sistema. Nesta PR ainda não há autenticação
 * (senhaHash fica nulo) — a tabela existe para já suportar o vínculo com
 * espaços via {@link UsuarioEspaco}.
 */
@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash")
    private String senhaHash;

    @Column(nullable = false)
    private String nome;

    @Builder.Default
    @Column(name = "precisa_trocar_senha", nullable = false)
    private boolean precisaTrocarSenha = false;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
