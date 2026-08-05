package com.financeiro.entity;

import com.financeiro.entity.enums.StatusAssinatura;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assinaturas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assinatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "espaco_id", nullable = false, unique = true)
    private Long espacoId;

    @Column(name = "plano_id", nullable = false)
    private Long planoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAssinatura status;

    @Column(name = "vigencia_inicio", nullable = false)
    private LocalDateTime vigenciaInicio;

    @Column(name = "vigencia_fim")
    private LocalDateTime vigenciaFim;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private LocalDateTime atualizadaEm;

    @PrePersist
    public void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (this.criadaEm == null) this.criadaEm = agora;
        if (this.atualizadaEm == null) this.atualizadaEm = agora;
        if (this.vigenciaInicio == null) this.vigenciaInicio = agora;
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadaEm = LocalDateTime.now();
    }
}
