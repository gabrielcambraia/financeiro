package com.financeiro.entity.enums;

/**
 * Papel global do usuário na plataforma — não confundir com {@link PapelUsuario}
 * (DONO/MEMBRO), que é por espaço. ADMIN gerencia recursos globais que não
 * pertencem a nenhum espaço específico (hoje, só o catálogo de bancos).
 */
public enum NivelAcesso {
    USUARIO, ADMIN
}
