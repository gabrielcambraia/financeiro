package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDateTime;
import com.financeiro.entity.enums.NivelAcesso;
import com.financeiro.entity.enums.PapelUsuario;
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

    @Column(name = "espaco_id", nullable = false)
    private Long espacoId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelUsuario papel = PapelUsuario.DONO;

    // Papel global (não por espaço) — só ADMIN gerencia recursos que não
    // pertencem a espaço nenhum (hoje, o catálogo de bancos).
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_acesso", nullable = false)
    private NivelAcesso nivelAcesso = NivelAcesso.USUARIO;

    @Column
    private String telefone;

    @Column(name = "email_verificado_em")
    private LocalDateTime emailVerificadoEm;

    @Column(name = "telefone_verificado_em")
    private LocalDateTime telefoneVerificadoEm;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
