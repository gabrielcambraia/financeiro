package com.financeiro.erro;

import com.financeiro.seguranca.FiltroIdRequisicao;
import com.financeiro.seguranca.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Ponto único de tratamento de erro: toda exceção não tratada pelo
 * controller é logada aqui com contexto completo (idRequisicao, usuário,
 * método+URI, stack trace) e traduzida para um {@link RespostaErro} enxuto
 * — o cliente nunca recebe stack trace nem a mensagem crua de exceções
 * internas.
 */
@Slf4j
@RestControllerAdvice
public class TratadorGlobalExcecoes {

    @ExceptionHandler(ExcecaoRecursoNaoEncontrado.class)
    public ResponseEntity<RespostaErro> tratarRecursoNaoEncontrado(ExcecaoRecursoNaoEncontrado ex,
                                                                     HttpServletRequest request) {
        log.warn("Recurso não encontrado: {} [{}]", ex.getMessage(), contextoRequisicao(request));
        return construirResposta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespostaErro> tratarValidacao(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .orElse("Dados inválidos");
        log.warn("Validação falhou: {} [{}]", mensagem, contextoRequisicao(request));
        return construirResposta(HttpStatus.BAD_REQUEST, mensagem);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RespostaErro> tratarResponseStatusException(ResponseStatusException ex,
                                                                        HttpServletRequest request) {
        log.warn("Requisição rejeitada: {} [{}]", ex.getReason(), contextoRequisicao(request));
        return construirResposta(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaErro> tratarErroInterno(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado [{}]", contextoRequisicao(request), ex);
        return construirResposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");
    }

    private ResponseEntity<RespostaErro> construirResposta(HttpStatus status, String mensagem) {
        RespostaErro corpo = new RespostaErro(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), mensagem, idRequisicaoAtual());
        return ResponseEntity.status(status).body(corpo);
    }

    private String contextoRequisicao(HttpServletRequest request) {
        String usuario = usuarioAtual();
        return request.getMethod() + " " + request.getRequestURI()
                + (usuario != null ? " usuario=" + usuario : "");
    }

    private String usuarioAtual() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return String.valueOf(usuario.usuarioId());
        }
        return null;
    }

    private String idRequisicaoAtual() {
        return MDC.get(FiltroIdRequisicao.CHAVE_MDC_ID_REQUISICAO);
    }
}
