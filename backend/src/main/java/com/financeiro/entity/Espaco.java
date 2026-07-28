package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDateTime;
import com.financeiro.entity.enums.ModuloEspaco;
import com.financeiro.entity.enums.PlanoEspaco;
import com.financeiro.entity.enums.TipoEspaco;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * O "tenant": todo dado (contas, categorias, transações) pertence a um
 * espaço, não diretamente a um usuário. Isso permite que uma pessoa física
 * tenha um espaço só dela e que uma empresa tenha vários usuários
 * compartilhando o mesmo espaço, sem mudar o modelo de dados.
 */
@Entity
@Table(name = "espacos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Espaco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEspaco tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanoEspaco plano;

    @Convert(converter = ConversorLocalDateTime.class)
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    // Opt-in, controlado só pela tela de admin — nenhum endpoint valida
    // isso ainda (ver ModuloEspaco).
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "espaco_modulos", joinColumns = @JoinColumn(name = "espaco_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "modulo")
    @Builder.Default
    private Set<ModuloEspaco> modulosHabilitados = new HashSet<>();

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }
}
