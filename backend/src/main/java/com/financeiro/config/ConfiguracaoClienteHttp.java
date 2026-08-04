package com.financeiro.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP genérico do backend (hoje usado só por
 * {@code ServicoIndiceEconomico} para consultar a API pública do Banco
 * Central). Timeouts curtos: uma falha/lentidão da API externa não pode
 * travar o startup nem o agendador mensal.
 */
@Configuration
public class ConfiguracaoClienteHttp {

    @Bean
    public RestClient restClient() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10));
        ClientHttpRequestFactory fabrica = ClientHttpRequestFactories.get(settings);
        return RestClient.builder()
                .requestFactory(fabrica)
                .build();
    }
}
