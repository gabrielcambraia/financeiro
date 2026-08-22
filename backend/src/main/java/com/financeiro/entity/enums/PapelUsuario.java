package com.financeiro.entity.enums;

/**
 * Papel do usuário dentro do seu espaço — não confundir com
 * {@link NivelAcesso}, que é um papel global da plataforma. DONO pode
 * gerenciar os demais usuários do espaço e os recursos restritos a dono
 * (ver {@code AutorizacaoEspaco.exigirDono}); MEMBRO tem acesso operacional.
 */
public enum PapelUsuario {
    DONO, MEMBRO
}
