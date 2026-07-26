package com.financeiro.seguranca;

import com.financeiro.entity.enums.NivelAcesso;

/**
 * Principal autenticado extraído do JWT: identifica o usuário e o espaço
 * (tenant) ativo na requisição corrente. {@code precisaTrocarSenha} vem do
 * claim do token e é usado por {@link FiltroTrocaSenhaObrigatoria} para
 * bloquear o uso normal da API até a troca de senha acontecer.
 */
public record UsuarioAutenticado(Long usuarioId, Long espacoId, String email, boolean precisaTrocarSenha,
                                  NivelAcesso nivelAcesso) {
}
