package com.financeiro.seguranca;

import com.financeiro.entity.enums.NivelAcesso;
import com.financeiro.entity.enums.PapelUsuario;

/**
 * Principal autenticado extraído do JWT: identifica o usuário e o espaço
 * (tenant) ativo na requisição corrente. {@code precisaTrocarSenha} e
 * {@code precisaCadastrarTelefone} vêm de claims do token e são usados por
 * {@link FiltroTrocaSenhaObrigatoria}/{@link FiltroCadastroTelefoneObrigatorio}
 * para bloquear o uso normal da API até o primeiro acesso ser concluído.
 */
public record UsuarioAutenticado(Long usuarioId, Long espacoId, String email, boolean precisaTrocarSenha,
                                  boolean precisaCadastrarTelefone, NivelAcesso nivelAcesso, PapelUsuario papel) {
}
