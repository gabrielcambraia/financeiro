package com.financeiro.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Origens permitidas vêm de {@code financeiro.cors.origens-permitidas}
 * (mesma propriedade usada por {@link com.financeiro.seguranca.FiltroProtecaoOrigem}
 * para validar a origem das rotas autenticadas por cookie). Em dev, o default
 * cobre as portas locais do Vite; em produção deve apontar para o domínio real.
 */
@Configuration
public class ConfiguracaoWeb implements WebMvcConfigurer {

    private final String[] origensPermitidas;

    public ConfiguracaoWeb(@Value("${financeiro.cors.origens-permitidas}") String[] origensPermitidas) {
        this.origensPermitidas = origensPermitidas;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origensPermitidas)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
