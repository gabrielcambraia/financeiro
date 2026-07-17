package com.financeiro.erro;

import java.time.LocalDateTime;

/**
 * Corpo JSON enxuto devolvido ao cliente em qualquer erro — nunca inclui
 * stack trace. O {@code idRequisicao} permite ao suporte cruzar o relato do
 * usuário com a linha completa (stack + contexto) logada no servidor.
 */
public record RespostaErro(LocalDateTime timestamp, int status, String erro, String mensagem, String idRequisicao) {
}
