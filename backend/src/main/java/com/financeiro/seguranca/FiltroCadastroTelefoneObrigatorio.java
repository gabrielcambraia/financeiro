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
 * {@code precisaCadastrarTelefone=true} — telefone é obrigatório (inclusive
 * retroativamente para contas antigas que se autorregistraram sem telefone,
 * quando isso ainda era opcional). Registrado depois de
 * {@link FiltroTrocaSenhaObrigatoria} na cadeia de segurança: quem está com
 * senha temporária cai no gate de senha primeiro, mas se os dois flags
 * estiverem ativos ({@code precisaTrocarSenha} e
 * {@code precisaCadastrarTelefone}), o token continua carregando o segundo
 * flag até a troca de senha reemitir um novo — por isso
 * {@code /api/auth/trocar-senha} também precisa estar na whitelist aqui,
 * senão esse filtro bloqueia a própria chamada que destravaria o outro.
 */
@Component
public class FiltroCadastroTelefoneObrigatorio extends OncePerRequestFilter {

    private static final Set<String> ROTAS_PERMITIDAS = Set.of(
            "/api/auth/telefone", "/api/auth/trocar-senha", "/api/auth/config", "/api/auth/login",
            "/api/auth/renovar", "/api/auth/sair");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();

        boolean bloqueado = autenticacao != null
                && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.precisaCadastrarTelefone()
                && request.getRequestURI().startsWith("/api/")
                && !ROTAS_PERMITIDAS.contains(request.getRequestURI());

        if (bloqueado) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"codigo\":\"TELEFONE_PENDENTE\",\"mensagem\":\"É necessário cadastrar um telefone antes de continuar\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
