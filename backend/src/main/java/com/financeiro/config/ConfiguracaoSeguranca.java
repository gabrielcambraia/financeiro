package com.financeiro.config;

import com.financeiro.seguranca.FiltroAutenticacaoJwt;
import com.financeiro.seguranca.FiltroIdRequisicao;
import com.financeiro.seguranca.FiltroLimiteTaxaAutenticacao;
import com.financeiro.seguranca.FiltroProtecaoOrigem;
import com.financeiro.seguranca.FiltroTrocaSenhaObrigatoria;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

/**
 * Cadeia de segurança única: exige JWT em toda a API, exceto /api/auth/register,
 * /api/auth/login e /api/auth/config. {@code @EnableMethodSecurity} habilita
 * {@code @PreAuthorize} para checagens de papel (ex.: DONO) direto nos
 * métodos de serviço, via bean SpEL {@code AutorizacaoEspaco}.
 */
@Configuration
@EnableMethodSecurity
public class ConfiguracaoSeguranca {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadeiaSeguranca(
            HttpSecurity http, FiltroAutenticacaoJwt filtroJwt, FiltroTrocaSenhaObrigatoria filtroTrocaSenha,
            FiltroLimiteTaxaAutenticacao filtroLimiteTaxa, FiltroProtecaoOrigem filtroProtecaoOrigem,
            FiltroIdRequisicao filtroIdRequisicao) throws Exception {
        http
                // CSRF "clássico" (token de sessão) não se aplica aqui: a API é stateless
                // e autentica via header Authorization (Bearer), que um site terceiro não
                // consegue anexar numa requisição cross-site. As duas rotas que dependem
                // só do cookie httpOnly (renovar/sair) são protegidas por FiltroProtecaoOrigem.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "script-src 'self'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "font-src 'self'; " +
                                        "connect-src 'self'; " +
                                        "object-src 'none'; " +
                                        "base-uri 'self'; " +
                                        "form-action 'self'; " +
                                        "frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "geolocation=(), camera=(), microphone=(), payment=()")))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/config",
                                "/api/auth/renovar", "/api/auth/sair").permitAll()
                        .requestMatchers("/api/actuator/health/liveness", "/api/actuator/health/readiness")
                        .permitAll()
                        .requestMatchers(req -> !req.getRequestURI().startsWith("/api/")).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(excecao -> excecao
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Não autenticado")))
                .addFilterBefore(filtroLimiteTaxa, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(filtroIdRequisicao, FiltroLimiteTaxaAutenticacao.class)
                .addFilterBefore(filtroProtecaoOrigem, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(filtroTrocaSenha, FiltroAutenticacaoJwt.class);

        return http.build();
    }
}
