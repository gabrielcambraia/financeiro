package com.financeiro.config;

import com.financeiro.seguranca.FiltroAutenticacaoJwt;
import com.financeiro.seguranca.FiltroTrocaSenhaObrigatoria;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cadeia de segurança única: exige JWT em toda a API, exceto /api/auth/register,
 * /api/auth/login e /api/auth/config.
 */
@Configuration
public class ConfiguracaoSeguranca {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadeiaSeguranca(
            HttpSecurity http, FiltroAutenticacaoJwt filtroJwt, FiltroTrocaSenhaObrigatoria filtroTrocaSenha) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/config").permitAll()
                        .requestMatchers(req -> !req.getRequestURI().startsWith("/api/")).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(excecao -> excecao
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Não autenticado")))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(filtroTrocaSenha, FiltroAutenticacaoJwt.class);

        return http.build();
    }
}
