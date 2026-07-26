package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDateTime;
import com.financeiro.entity.enums.NivelAcesso;
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

    // Papel global (não por espaço, ver PapelUsuario) — só ADMIN gerencia
    // recursos que não pertencem a espaço nenhum (hoje, o catálogo de bancos).
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_acesso", nullable = false)
    private NivelAcesso nivelAcesso = NivelAcesso.USUARIO;

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
