package com.financeiro.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuração global da plataforma — singleton, sempre a linha id=1
 * (seedada pela migration). Guarda dois slots de logo independentes: `logo`
 * (favicon do navegador e ícone da barra lateral) e `logoLogin` (banner
 * exibido na tela de login, no lugar do ícone+nome padrão). Os bytes ficam
 * no banco (não em disco: Render tem filesystem efêmero).
 */
@Entity
@Table(name = "configuracao_plataforma")
@Data
@NoArgsConstructor
public class ConfiguracaoPlataforma {

    public static final Long ID_UNICO = 1L;

    @Id
    private Long id;

    private byte[] logo;

    private String logoTipo;

    private byte[] logoLogin;

    private String logoLoginTipo;
}
