package com.financeiro.entity.enums;

/**
 * Módulo opcional que pode ser habilitado por espaço (opt-in). Hoje é só
 * um catálogo — nenhum endpoint valida se o espaço tem o módulo habilitado
 * antes de atender a requisição. WHATSAPP_IA é o primeiro módulo, ainda
 * sem funcionalidade real por trás, cadastrado para validar a tela de
 * admin que vai controlar isso.
 */
public enum ModuloEspaco {
    WHATSAPP_IA
}
