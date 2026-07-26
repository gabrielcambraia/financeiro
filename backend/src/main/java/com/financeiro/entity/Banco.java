package com.financeiro.entity;

import com.financeiro.entity.converter.ConversorLocalDateTime;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Catálogo global de bancos/instituições — não é escopado por espaço, é
 * compartilhado por todos os tenants. Gerenciado só por administradores
 * (ver {@link com.financeiro.seguranca.AutorizacaoAdmin}). O logo fica
 * guardado como bytes no próprio banco de dados (nada de disco local: a
 * hospedagem em Render tem filesystem efêmero, some a cada deploy).
 */
@Entity
@Table(name = "bancos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "cor_primaria", nullable = false)
    private String corPrimaria;

    @Column(nullable = false)
    private String sigla;

    private byte[] logo;

    @Column(name = "logo_tipo")
    private String logoTipo;

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
