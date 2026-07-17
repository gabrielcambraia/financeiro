package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDateTime;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Um refresh token opaco (só o hash SHA-256 é persistido). A janela de
 * inatividade é implementada como um campo mutável: {@code expiraEm} é
 * reescrito a cada renovação, então a sessão só morre se o usuário ficar
 * {@code TTL} sem chamar {@code /api/auth/renovar}.
 */
@Entity
@Table(name = "tokens_atualizacao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenAtualizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Builder.Default
    @Column(nullable = false)
    private boolean revogado = false;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    @Column(name = "substituido_por")
    private String substituidoPor;

    @Column(name = "user_agent")
    private String userAgent;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
