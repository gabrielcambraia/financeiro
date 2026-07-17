package com.financeiro.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Bloqueia o uso normal da API enquanto o usuário autenticado tiver
 * {@code precisaTrocarSenha=true} (membro recém-criado por um DONO, ainda
 * com a senha temporária). Só a rota de troca de senha e a de descoberta de
 * modo continuam acessíveis — qualquer outra chamada a /api/** recebe 403
 * com um código que o frontend reconhece para redirecionar.
 */
@Component
public class FiltroTrocaSenhaObrigatoria extends OncePerRequestFilter {

    private static final Set<String> ROTAS_PERMITIDAS = Set.of(
            "/api/auth/trocar-senha", "/api/auth/config", "/api/auth/login",
            "/api/auth/renovar", "/api/auth/sair");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();

        boolean bloqueado = autenticacao != null
                && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.precisaTrocarSenha()
                && request.getRequestURI().startsWith("/api/")
                && !ROTAS_PERMITIDAS.contains(request.getRequestURI());

        if (bloqueado) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"codigo\":\"SENHA_TEMPORARIA\",\"mensagem\":\"É necessário trocar a senha temporária antes de continuar\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
