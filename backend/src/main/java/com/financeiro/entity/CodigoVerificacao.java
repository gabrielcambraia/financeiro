package com.financeiro.entity;

import com.financeiro.entity.enums.CanalNotificacao;
import com.financeiro.entity.enums.PropositoCodigo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "codigos_verificacao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoVerificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private String destinatario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalNotificacao canal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropositoCodigo proposito;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @Builder.Default
    @Column(nullable = false)
    private int tentativas = 0;

    @Column(name = "ip_origem")
    private String ipOrigem;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
