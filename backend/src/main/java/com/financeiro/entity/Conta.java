package com.financeiro.entity;

import com.financeiro.entity.enums.TipoConta;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "contas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoConta tipo;

    @Column(nullable = false)
    private BigDecimal saldo;

    @Column(nullable = false)
    private String cor;

    @Column(nullable = false)
    private String icone;
}
